package de.unisb.cs.st.javaslicer.tracer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import de.hammacher.util.ConcurrentReferenceHashMap;
import de.hammacher.util.MultiplexedFileWriter;
import de.hammacher.util.SimpleArrayList;
import de.hammacher.util.StringCacheOutput;
import de.hammacher.util.ConcurrentReferenceHashMap.Option;
import de.hammacher.util.ConcurrentReferenceHashMap.ReferenceType;
import de.hammacher.util.MultiplexedFileWriter.MultiplexOutputStream;
import de.unisb.cs.st.javaslicer.common.TraceSequenceTypes;
import de.unisb.cs.st.javaslicer.common.classRepresentation.ReadClass;
import de.unisb.cs.st.javaslicer.common.exceptions.TracerException;
import de.unisb.cs.st.javaslicer.tracer.instrumentation.TracingMethodInstrumenter;
import de.unisb.cs.st.javaslicer.tracer.instrumentation.Transformer;
import de.unisb.cs.st.javaslicer.tracer.traceSequences.ObjectIdentifier;
import de.unisb.cs.st.javaslicer.tracer.traceSequences.TraceSequenceFactory;

public class Tracer {

    private static Tracer instance = null;

    public final boolean debug;
    public final boolean check;

    protected final TraceSequenceFactory seqFactory;

    private final Map<Thread, ThreadTracer> threadTracers;

    // an (untraced) list that holds ThreadTracers that can be finished. They are added to this
    // list first, because if they would be finished immediately, it would leed to an recursive
    // loop...
    protected SimpleArrayList<TracingThreadTracer> readyThreadTracers = new SimpleArrayList<TracingThreadTracer>();

    protected final List<TraceSequenceTypes.Type> traceSequenceTypes
        = Collections.synchronizedList(new SimpleArrayList<TraceSequenceTypes.Type>());

    public volatile boolean tracingStarted = false;
    public volatile boolean tracingReady = false;

    private final MultiplexedFileWriter file;
    private final ConcurrentLinkedQueue<ReadClass> readClasses = new ConcurrentLinkedQueue<ReadClass>();
    private final StringCacheOutput readClassesStringCache = new StringCacheOutput();
    private final DataOutputStream readClassesOutputStream;
    private final DataOutputStream threadTracersOutputStream;

    private Set<String> notRedefinedClasses;

    // there are classes needed while retransforming.
    // these must be loaded a-priori, otherwise circular dependencies may occur
    private final String[] classesToPreload = {
            "java.io.IOException",
            "java.io.EOFException",
            NullThreadTracer.class.getName(),
    };

    private final ConcurrentMap<ThreadTracer, CountDownLatch> writtenThreadTracers =
        new ConcurrentReferenceHashMap<ThreadTracer, CountDownLatch>(
            32, .75f, 16, ReferenceType.WEAK, ReferenceType.STRONG,
            EnumSet.of(Option.IDENTITY_COMPARISONS));

    // the thread that just creates a threadTracer (needed to avoid stack overflowes)
    private Thread threadTracerBeingCreated = null;

    private final AtomicInteger errorCount = new AtomicInteger(0);

    private String lastErrorString;

    private final Transformer transformer;


    private Tracer(final File filename, final boolean debug, final boolean check,
            final TraceSequenceFactory seqFac, final Instrumentation instrumentation) throws IOException {
        this.debug = debug;
        this.check = check;
        this.seqFactory = seqFac;
        this.transformer = new Transformer(this, instrumentation, this.readClasses);
        this.file = new MultiplexedFileWriter(filename, 512, MultiplexedFileWriter.is64bitVM,
                ByteOrder.nativeOrder(), seqFac.shouldAutoFlushFile());
        this.file.setReuseStreamIds(true);
        final MultiplexOutputStream readClassesMultiplexedStream = this.file.newOutputStream();
        if (readClassesMultiplexedStream.getId() != 0)
            throw new AssertionError("MultiplexedFileWriter does not initially return stream id 0");
        this.readClassesOutputStream = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(readClassesMultiplexedStream, 512), 512));
        final MultiplexOutputStream threadTracersMultiplexedStream = this.file.newOutputStream();
        if (threadTracersMultiplexedStream.getId() != 1)
            throw new AssertionError("MultiplexedFileWriter does not monotonously increase stream ids");
        this.threadTracersOutputStream = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(threadTracersMultiplexedStream, 512), 512));
        final ConcurrentReferenceHashMap<Thread, ThreadTracer> threadTracersMap =
            new ConcurrentReferenceHashMap<Thread, ThreadTracer>(
                    32, .75f, 16, ReferenceType.WEAK, ReferenceType.STRONG,
                    EnumSet.of(Option.IDENTITY_COMPARISONS));
        threadTracersMap.addRemoveStaleListener(new ConcurrentReferenceHashMap.RemoveStaleListener<ThreadTracer>() {
            public void removed(final ThreadTracer removedValue) {
                if (removedValue instanceof TracingThreadTracer) {
                    synchronized (Tracer.this.readyThreadTracers) {
                        Tracer.this.readyThreadTracers.add((TracingThreadTracer) removedValue);
                    }
                }
            }
        });
        this.threadTracers = threadTracersMap;
    }

    public void error(final Exception e) {
        if (this.debug) {
            final StringWriter sw = new StringWriter();
            final PrintWriter ps = new PrintWriter(sw);
            e.printStackTrace(ps);
            System.err.println(this.lastErrorString = sw.toString());
        } else {
            System.err.println(this.lastErrorString = e.toString());
        }
        this.errorCount.getAndIncrement();
    }

    public static void newInstance(final File filename, final boolean debug, final boolean check,
            final TraceSequenceFactory seqFac, final Instrumentation instrumentation) throws IOException {
        if (instance != null)
            throw new IllegalStateException("Tracer instance already exists");
        instance = new Tracer(filename, debug, check, seqFac, instrumentation);
    }

    public static Tracer getInstance() {
        if (instance == null)
            throw new IllegalStateException("Tracer instance not created");
        return instance;
    }

    public void add(final Instrumentation inst, final boolean retransformClasses) throws TracerException {

        // check the JRE version we run on
        final String javaVersion = System.getProperty("java.version");
        final int secondPointPos = javaVersion.indexOf('.', javaVersion.indexOf('.')+1);
        try {
            if (secondPointPos != -1) {
                final double javaVersionDouble = Double.valueOf(javaVersion.substring(0, secondPointPos));
                if (javaVersionDouble < 1.59) {
                    System.err.println("This tracer requires JRE >= 1.6, you are running " + javaVersion + ".");
                    System.exit(-1);
                }
            }
        } catch (final NumberFormatException e) {
            // ignore (no check...)
        }

        if (retransformClasses && !inst.isRetransformClassesSupported())
            throw new TracerException("Your JVM does not support retransformation of classes");

        final List<Class<?>> additionalClassesToRetransform = new ArrayList<Class<?>>();
        for (final String classname: this.classesToPreload) {
            Class<?> class1;
            try {
                class1 = ClassLoader.getSystemClassLoader().loadClass(classname);
            } catch (final ClassNotFoundException e) {
                continue;
            }
            additionalClassesToRetransform.add(class1);
        }

        // call a method in ObjectIdentifier to ensure that the class is initialized
        ObjectIdentifier.instance.getObjectId(this);

        this.notRedefinedClasses = new HashSet<String>();
        for (final Class<?> class1: additionalClassesToRetransform)
            this.notRedefinedClasses.add(class1.getName());
        for (final Class<?> class1: inst.getAllLoadedClasses())
            this.notRedefinedClasses.add(class1.getName());

        inst.addTransformer(this.transformer, true);

        if (retransformClasses) {
            final ArrayList<Class<?>> classesToRetransform = new ArrayList<Class<?>>();
            for (final Class<?> class1: inst.getAllLoadedClasses()) {
                final boolean isModifiable = inst.isModifiableClass(class1);
                if (this.debug && !isModifiable && !class1.isPrimitive() && !class1.isArray())
                    System.out.println("not modifiable: " + class1);
                boolean modify = isModifiable && !class1.isInterface();
                modify &= !class1.getName().startsWith("de.unisb.cs.st.javaslicer.tracer");
                if (modify)
                    classesToRetransform.add(class1);
            }
            for (final Class<?> class1: additionalClassesToRetransform) {
                final boolean isModifiable = inst.isModifiableClass(class1);
                if (this.debug && !isModifiable && !class1.isPrimitive() && !class1.isArray())
                    System.out.println("not modifiable: " + class1);
                boolean modify = isModifiable && !class1.isInterface();
                modify &= !class1.getName().startsWith("de.unisb.cs.st.javaslicer.tracer");
                if (modify && !classesToRetransform.contains(class1)) {
                    classesToRetransform.add(class1);
                }
            }

            if (this.debug) {
                System.out.println("classes to retransform (" + classesToRetransform.size() + "):");
                for (final Class<?> c1 : classesToRetransform) {
                    System.out.println(c1);
                }
                System.out.println("############################################");
            }

            try {
                inst.retransformClasses(classesToRetransform.toArray(new Class<?>[classesToRetransform.size()]));
                if (this.debug)
                    System.out.println("Initial instrumentation ready");
            } catch (final UnmodifiableClassException e) {
                throw new TracerException(e);
            }

            if (this.debug) {
                // print statistics once now and once when all finished (in finish() method)
                TracingMethodInstrumenter.printStats(System.out);
            }
        }

        synchronized (this.threadTracers) {
            this.tracingStarted = true;
            if (!this.tracingReady) {
                for (final ThreadTracer tt: this.threadTracers.values())
                    tt.unpauseTracing();
            }
        }
    }

    public int getNextSequenceIndex() {
        return this.traceSequenceTypes.size();
    }

    public int newIntegerTraceSequence() {
        return newTraceSequence(TraceSequenceTypes.Type.INTEGER);
    }

    public int newLongTraceSequence() {
        return newTraceSequence(TraceSequenceTypes.Type.LONG);
    }

    private synchronized int newTraceSequence(final TraceSequenceTypes.Type type) {
        final int nextIndex = getNextSequenceIndex();
        this.traceSequenceTypes.add(type);
        return nextIndex;
    }

    /**
     * Returns the {@link ThreadTracer} associated with the current (calling) thread.
     *
     * If no {@link ThreadTracer} exists so far, a new one is created, or a
     * {@link NullThreadTracer} is returned if the Thread implements {@link UntracedThread}.
     *
     * @return the {@link ThreadTracer} associated with the current (calling) thread
     */
    public ThreadTracer getThreadTracer() {
        final Thread currentThread = Thread.currentThread();
        // exclude all (internal) untraced threads
        if (currentThread instanceof UntracedThread)
            return NullThreadTracer.instance;
        ThreadTracer tracer = this.threadTracers.get(currentThread);
        if (tracer != null)
            return tracer;
        final ThreadTracer newTracer;
        synchronized (this.threadTracers) {
            // check if it's present now (should not be the case)...
            tracer = this.threadTracers.get(currentThread);
            if (tracer != null)
                return tracer;
            if (this.threadTracerBeingCreated == currentThread)
                return NullThreadTracer.instance;
            assert this.threadTracerBeingCreated == null;
            this.threadTracerBeingCreated = currentThread;
            // exclude the MultiplexedFileWriter autoflush thread
            if (this.tracingReady ||
                    currentThread.getClass().getName().startsWith(MultiplexedFileWriter.class.getName()))
                newTracer = NullThreadTracer.instance;
            else
                newTracer = new TracingThreadTracer(currentThread,
                            this.traceSequenceTypes, this);
            try {
                // we have to pause it, because put uses classes in the java api
                newTracer.pauseTracing();
                final ThreadTracer oldTracer = this.threadTracers.put(currentThread, newTracer);
                assert oldTracer == null;
            } finally {
                assert this.threadTracerBeingCreated == currentThread;
                this.threadTracerBeingCreated = null;
                // recheck tracingReady!
                if (this.tracingStarted && !this.tracingReady)
                    newTracer.unpauseTracing();
            }
        }
        synchronized (this.readyThreadTracers) {
            if (this.readyThreadTracers.size() > 0) {
                newTracer.pauseTracing();
                try {
                    for (final TracingThreadTracer t: this.readyThreadTracers)
                        writeOutIfNecessary(t);
                } finally {
                    newTracer.unpauseTracing();
                }
            }
        }
        return newTracer;
    }

    public void threadExits() {
        try {
            final Thread exitingThread = Thread.currentThread();
            final ThreadTracer threadTracer = this.threadTracers.get(exitingThread);
            if (threadTracer != null)
                threadTracer.pauseTracing();
            if (threadTracer instanceof TracingThreadTracer) {
                final TracingThreadTracer ttt = (TracingThreadTracer) threadTracer;
                assert ttt.getThreadId() == exitingThread.getId();
                writeOutIfNecessary(ttt);
            }
            this.threadTracers.put(exitingThread, NullThreadTracer.instance);
        } catch (final Throwable t) {
            t.printStackTrace();
        }
    }

    private void writeOutIfNecessary(final TracingThreadTracer threadTracer) {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch oldLatch = this.writtenThreadTracers.putIfAbsent(threadTracer, latch);
        if (oldLatch == null) {
            try {
                threadTracer.finish();
                synchronized (this.threadTracersOutputStream) {
                    threadTracer.writeOut(this.threadTracersOutputStream);
                }
            } catch (final IOException e) {
                error(e);
            } finally {
                latch.countDown();
            }
        } else {
            try {
                oldLatch.await();
            } catch (final InterruptedException e) {
                // reset interrupted flag, but continue
                Thread.currentThread().interrupt();
            }
        }
    }

    private final Object finishLock = new Object();
    public void finish() throws IOException {
        synchronized (this.finishLock) {
            if (this.tracingReady)
                return;
            this.tracingReady = true;
            this.transformer.finish();
            final List<TracingThreadTracer> allThreadTracers = new ArrayList<TracingThreadTracer>();
            synchronized (this.threadTracers) {
                for (final Entry<Thread, ThreadTracer> e: this.threadTracers.entrySet()) {
                    final ThreadTracer t = e.getValue();
                    e.setValue(NullThreadTracer.instance);
                    if (t instanceof TracingThreadTracer)
                        allThreadTracers.add((TracingThreadTracer) t);
                }
            }
            synchronized (this.readyThreadTracers) {
                allThreadTracers.addAll(this.readyThreadTracers);
                this.readyThreadTracers.clear();
            }
            for (final TracingThreadTracer t: allThreadTracers) {
                writeOutIfNecessary(t);
            }
            this.threadTracersOutputStream.close();

            ReadClass rc;
            while ((rc = this.readClasses.poll()) != null)
                rc.writeOut(this.readClassesOutputStream, this.readClassesStringCache);
            this.readClassesOutputStream.close();
            this.file.close();
        }
    }

    public MultiplexOutputStream newOutputStream() {
        return this.file.newOutputStream();
    }

    public void printFinalUserInfo() {
        if (this.errorCount.get() == 1) {
            System.out.println("There was an error while tracing: " + this.lastErrorString);
        } else if (this.errorCount.get() > 1) {
            System.out.println("There were several errors (" + this.errorCount.get() + ") while tracing.");
            System.out.println("Last error message: " + this.lastErrorString);
        } else {
            if (this.debug)
                System.out.println("DEBUG: trace written successfully");
        }
    }

    /**
     * Checks whether the class given by the fully qualified java class name has been
     * redefined by the instrumenter or not.
     * The classes that couldn't get redefined are those already loaded by the vm when
     * the agent's premain method was executed.
     *
     * @param className the fully qualified classname to check
     * @return true if the class was redefined, false if not
     */
    // hmm, redefined is the wrong word here...
    public boolean wasRedefined(final String className) {
        return !this.notRedefinedClasses.contains(className);
    }

}