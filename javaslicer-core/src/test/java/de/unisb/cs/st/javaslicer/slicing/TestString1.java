package de.unisb.cs.st.javaslicer.slicing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import de.unisb.cs.st.javaslicer.AbstractSlicingTest;
import de.unisb.cs.st.javaslicer.common.classRepresentation.Instruction;


@Ignore
public class TestString1 extends AbstractSlicingTest {

    @Test
    public void testAll() throws IllegalArgumentException, IOException, URISyntaxException, InterruptedException {
        Assert.fail();
        final List<Instruction> slice = getSlice("/traces/simple2", "main", "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:11:{a,b,c,d,e}");
        checkSlice(slice, new String[] {
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 ALOAD 0",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 ICONST_0",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 AALOAD",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 ICONST_0",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 INVOKEVIRTUAL java/lang/String.charAt(I)C",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 BIPUSH 48",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 ISUB",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:7 ISTORE 1",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:8 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:8 ILOAD 1",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:8 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:8 ISTORE 2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:9 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:9 ILOAD 1",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:9 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:9 ISTORE 3",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:10 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:10 ILOAD 3",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:10 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:10 ISTORE 4",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:11 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:11 ILOAD 2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:11 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:11 ISTORE 5",
            });
    }

}