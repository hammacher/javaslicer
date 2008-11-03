package de.unisb.cs.st.javaslicer.tracer.traceResult;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

import de.unisb.cs.st.javaslicer.tracer.classRepresentation.Instruction;
import de.unisb.cs.st.javaslicer.tracer.classRepresentation.ReadClass;
import de.unisb.cs.st.javaslicer.tracer.classRepresentation.ReadMethod;
import de.unisb.cs.st.javaslicer.tracer.classRepresentation.StringCacheInput;
import de.unisb.cs.st.javaslicer.tracer.classRepresentation.Instruction.Instance;
import de.unisb.cs.st.javaslicer.tracer.classRepresentation.instructions.AbstractInstruction;
import de.unisb.cs.st.javaslicer.tracer.exceptions.TracerException;
import de.unisb.cs.st.javaslicer.tracer.traceResult.ThreadTraceResult.BackwardInstructionIterator;
import de.unisb.cs.st.javaslicer.tracer.util.MultiplexedFileReader;
import de.unisb.cs.st.javaslicer.tracer.util.MultiplexedFileReader.MultiplexInputStream;

public class TraceResult {

    public static class ThreadId implements Comparable<ThreadId> {

        private final long threadId;
        private final String threadName;

        public ThreadId(final long threadId, final String threadName) {
            this.threadId = threadId;
            this.threadName = threadName;
        }

        public long getThreadId() {
            return this.threadId;
        }

        public String getThreadName() {
            return this.threadName;
        }

        @Override
        public String toString() {
            return this.threadId + ": " + this.threadName;
        }

        public int compareTo(final ThreadId other) {
            if (this.threadId == other.threadId) {
                final int nameCmp = this.threadName.compareTo(other.threadName);
                if (nameCmp == 0 && this != other)
                    return System.identityHashCode(this) - System.identityHashCode(other);
                return nameCmp;
            }
            return Long.signum(this.threadId - other.threadId);
        }

    }

    private final List<ReadClass> readClasses;
    private final List<ThreadTraceResult> threadTraces;

    private final Instruction[] instructions;

    public TraceResult(final List<ReadClass> readClasses, final List<ThreadTraceResult> threadTraces) throws IOException {
        this.readClasses = readClasses;
        this.threadTraces = threadTraces;
        this.instructions = getInstructionArray(readClasses);
    }

    private static Instruction[] getInstructionArray(final List<ReadClass> classes) throws IOException {
        int numInstructions = 0;
        for (final ReadClass c: classes)
            if (c.getInstructionNumberEnd() > numInstructions)
                numInstructions = c.getInstructionNumberEnd();
        final Instruction[] instructions = new Instruction[numInstructions];
        int written = 0;
        for (final ReadClass c: classes) {
            for (final ReadMethod m: c.getMethods()) {
                written += m.getInstructions().size();
                for (final AbstractInstruction instr: m.getInstructions()) {
                    if (instructions[instr.getIndex()] != null)
                        throw new IOException("Same instruction index given twice.");
                    instructions[instr.getIndex()] = instr;
                }
            }
        }

        if (written != numInstructions)
            throw new IOException("Omitted some instruction indexes.");

        return instructions;
    }

    public static TraceResult readFrom(final File filename) throws IOException {
        final MultiplexedFileReader file = new MultiplexedFileReader(filename);
        if (file.getStreamIds().size() < 2)
            throw new IOException("corrupted data");
        final MultiplexInputStream readClassesStream = file.getInputStream(0);
        if (readClassesStream == null)
            throw new IOException("corrupted data");
        PushbackInputStream pushBackInput =
            new PushbackInputStream(new BufferedInputStream(
                    new GZIPInputStream(readClassesStream, 512), 512), 1);
        final DataInputStream readClassesInputStream = new DataInputStream(
                pushBackInput);
        final ArrayList<ReadClass> readClasses = new ArrayList<ReadClass>();
        final StringCacheInput stringCache = new StringCacheInput();
        int testRead;
        while ((testRead = pushBackInput.read()) != -1) {
            pushBackInput.unread(testRead);
            readClasses.add(ReadClass.readFrom(readClassesInputStream, stringCache));
        }
        readClasses.trimToSize();
        Collections.sort(readClasses, new Comparator<ReadClass>() {
            @Override
            public int compare(final ReadClass o1, final ReadClass o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        final MultiplexInputStream threadTracersStream = file.getInputStream(1);
        if (threadTracersStream == null)
            throw new IOException("corrupted data");
        pushBackInput = new PushbackInputStream(new BufferedInputStream(
                new GZIPInputStream(threadTracersStream, 512), 512), 1);
        final DataInputStream threadTracersInputStream = new DataInputStream(
                pushBackInput);
        final ArrayList<ThreadTraceResult> threadTraces = new ArrayList<ThreadTraceResult>();
        final TraceResult traceResult = new TraceResult(readClasses, threadTraces);
        while ((testRead = pushBackInput.read()) != -1) {
            pushBackInput.unread(testRead);
            threadTraces.add(ThreadTraceResult.readFrom(threadTracersInputStream, traceResult, file));
        }
        threadTraces.trimToSize();
        Collections.sort(threadTraces, new Comparator<ThreadTraceResult>() {
            @Override
            public int compare(final ThreadTraceResult o1, final ThreadTraceResult o2) {
                final long id1 = o1.getThreadId();
                final long id2 = o2.getThreadId();
                return id1 < id2 ? -1 : id1 == id2 ? 0 : 1;
            }
        });

        return traceResult;
    }

    public Iterator<Instance> getBackwardIterator(final long threadId) {
        final ThreadTraceResult res = findThreadTraceResult(threadId);
        return res == null ? null : res.getBackwardIterator();
    }

    private ThreadTraceResult findThreadTraceResult(final long threadId) {
        // binary search
        int left = 0;
        int right = this.threadTraces.size();
        int mid;

        while ((mid = (left + right) / 2) != left) {
            final ThreadTraceResult midVal = this.threadTraces.get(mid);
            if (midVal.getThreadId() <= threadId)
                left = mid;
            else
                right = mid;
        }

        final ThreadTraceResult found = this.threadTraces.get(mid);
        return found.getThreadId() == threadId ? found : null;
    }

    /**
     * Returns a sorted List of all threads that are represented
     * by traces in this TraceResult.
     *
     * @return the sorted list of {@link ThreadId}s.
     */
    public List<ThreadId> getThreads() {
        final List<ThreadId> list = new ArrayList<ThreadId>(this.threadTraces.size());
        for (final ThreadTraceResult tt: this.threadTraces)
            list.add(new ThreadId(tt.getThreadId(), tt.getThreadName()));
        return list;
    }

    public List<ReadClass> getReadClasses() {
        return this.readClasses;
    }

    public Instruction getInstruction(final int index) {
        if (index < 0 || index >= this.instructions.length)
            return null;
        return this.instructions[index];
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java " + TraceResult.class.getName() + " <file> [<threadId>]");
            System.exit(-1);
        }
        final File traceFile = new File(args[0]);
        Long threadToTrace = null;
        if (args.length > 1) {
            try {
                threadToTrace = Long.valueOf(args[1]);
            } catch (final NumberFormatException e) {
                System.err.println("Second parameter indicates the thread id to trace. Must be an integer.");
            }
        }

        System.out.println("Opening and reading trace file...");
        TraceResult tr = null;
        try {
            tr = readFrom(traceFile);
        } catch (final IOException e) {
            System.err.println("Error opening trace file: " + e);
            System.exit(-1);
            return;
        }

        final List<ThreadId> threads = tr.getThreads();
        if (threads.size() == 0) {
            System.err.println("The trace file contains no tracing information.");
            System.exit(-1);
        }

        System.out.println("The trace file contains traces for these threads:");
        ThreadId tracing = null;
        for (final ThreadId t: threads) {
            if (threadToTrace == null) {
                if ("main".equals(t.getThreadName()) && (tracing == null || t.getThreadId() < tracing.getThreadId()))
                    tracing = t;
            } else if (t.getThreadId() == threadToTrace.longValue()) {
                tracing = t;
            }
            System.out.format("%15d: %s%n", t.getThreadId(), t.getThreadName());
        }
        System.out.println();

        if (tracing == null) {
            System.out.println(threadToTrace == null ? "Couldn't find a main thread."
                    : "The thread you selected was not found.");
            System.exit(-1);
            return;
        }

        System.out.println(threadToTrace == null ? "Selected:" : "You selected:");
        System.out.format("%15d: %s%n", tracing.getThreadId(), tracing.getThreadName());

        try {
            System.out.println();
            System.out.println("The backward trace:");
            final Iterator<Instance> it = tr.getBackwardIterator(tracing.getThreadId());
            long nr = 0;
            final String format = "%8d: %-100s -> %3d %7d %s%n";
            System.out.format("%8s  %-100s    %3s %7s %s%n",
                    "Nr", "Location", "Dep", "OccNr", "Instruction");
            while (it.hasNext()) {
                final Instance inst = it.next();
                final ReadMethod method = inst.getMethod();
                final ReadClass class0 = method.getReadClass();
                System.out.format(format, nr++, class0.getName()+"."
                        +method.getName()+":"+inst.getLineNumber(),
                        inst.getStackDepth(),
                        inst.getOccurenceNumber(), inst.toString());
            }

            final BackwardInstructionIterator it2 = (BackwardInstructionIterator) it;

            System.out.println();
            System.out.println("No instructions: " + it2.getNoInstructions()
                    + " (+ " + it2.getNoAdditionalInstructions() + " additional = "
                    + (it2.getNoInstructions() + it2.getNoAdditionalInstructions())
                    + " total instructions)");

            System.out.println("Ready");
        } catch (final TracerException e) {
            System.err.println("Error while tracing: " + e.getMessage());
            System.exit(-1);
        }
    }

}
