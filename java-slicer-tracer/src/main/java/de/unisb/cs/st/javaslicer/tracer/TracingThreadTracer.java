package de.unisb.cs.st.javaslicer.tracer;

import java.io.BufferedWriter;
import java.io.DataOutput;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map.Entry;

import de.unisb.cs.st.javaslicer.tracer.traceSequences.ObjectTraceSequence;
import de.unisb.cs.st.javaslicer.tracer.traceSequences.TraceSequence;
import de.unisb.cs.st.javaslicer.tracer.traceSequences.TraceSequence.IntegerTraceSequence;
import de.unisb.cs.st.javaslicer.tracer.traceSequences.TraceSequence.Type;
import de.unisb.cs.st.javaslicer.tracer.util.IntegerMap;

public class TracingThreadTracer implements ThreadTracer {

    private final long threadId;
    private final String threadName;
    private final List<Type> threadSequenceTypes;

    private volatile int lastInstructionIndex = -1;

    private final IntegerMap<TraceSequence> sequences = new IntegerMap<TraceSequence>();

    private final Tracer tracer;
    private volatile int paused = 0;

    protected static PrintWriter debugFile;
    static {
        if (Tracer.debug) {
            try {
                debugFile = new PrintWriter(new BufferedWriter(new FileWriter(new File("debug.log"))));
                Runtime.getRuntime().addShutdownHook(new Thread("debug file closer") {
                    @Override
                    public void run() {
                        debugFile.close();
                    }
                });
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

    public TracingThreadTracer(final Thread thread,
            final List<Type> threadSequenceTypes, final Tracer tracer) {
        this.threadId = thread.getId();
        this.threadName = thread.getName();
        this.threadSequenceTypes = threadSequenceTypes;
        this.tracer = tracer;
    }

    public synchronized void traceInt(final int value, final int traceSequenceIndex) {
        if (this.paused > 0)
            return;

        pauseTracing();

        TraceSequence seq = this.sequences.get(traceSequenceIndex);
        try {
            if (seq == null) {
                seq = Tracer.seqFactory.createTraceSequence(
                        this.threadSequenceTypes.get(traceSequenceIndex), this.tracer);
                this.sequences.put(traceSequenceIndex, seq);
            }
            assert seq instanceof IntegerTraceSequence;

            ((IntegerTraceSequence) seq).trace(value);
        } catch (final IOException e) {
            Tracer.error(e);
            System.err.println("Error writing the trace: " + e.getMessage());
            System.exit(-1);
        }

        unpauseTracing();
    }

    public synchronized void traceObject(final Object obj, final int traceSequenceIndex) {
        if (this.paused > 0)
            return;

        pauseTracing();

        TraceSequence seq = this.sequences.get(traceSequenceIndex);
        try {
            if (seq == null) {
                seq = Tracer.seqFactory.createTraceSequence(
                        this.threadSequenceTypes.get(traceSequenceIndex), this.tracer);
                this.sequences.put(traceSequenceIndex, seq);
            }
            assert seq instanceof ObjectTraceSequence;

            ((ObjectTraceSequence) seq).trace(obj);
        } catch (final IOException e) {
            Tracer.error(e);
            System.err.println("Error writing the trace: " + e.getMessage());
            System.exit(-1);
        }

        unpauseTracing();
    }

    public void traceLastInstructionIndex(final int traceSequenceIndex) {
        traceInt(this.lastInstructionIndex, traceSequenceIndex);
    }

    public void passInstruction(final int instructionIndex) {
        if (this.paused > 0)
            return;

        if (Tracer.debug && this.threadId == 1) {
            pauseTracing();
            debugFile.println(instructionIndex);
            unpauseTracing();
        }

        this.lastInstructionIndex = instructionIndex;
    }

    public synchronized void finish() throws IOException {
        pauseTracing();

        for (final TraceSequence seq: this.sequences.values())
            seq.finish();
    }

    public void writeOut(final DataOutput out) throws IOException {
        finish();
        out.writeLong(this.threadId);
        out.writeUTF(this.threadName);
        out.writeInt(this.sequences.size());
        for (final Entry<Integer, TraceSequence> seq: this.sequences.entrySet()) {
            out.writeInt(seq.getKey());
            seq.getValue().writeOut(out);
        }
        out.writeInt(this.lastInstructionIndex);
    }

    public synchronized void pauseTracing() {
        ++this.paused;
    }

    public synchronized void unpauseTracing() {
        --this.paused;
        assert this.paused >= 0: "unpaused more than paused";
    }

    public boolean isPaused() {
        return this.paused > 0;
    }

    public long getThreadId() {
        return this.threadId;
    }

}