/** License information:
 *    Component: javaslicer-core
 *    Package:   de.unisb.cs.st.javaslicer.slicing
 *    Class:     TestString1
 *    Filename:  javaslicer-core/src/test/java/de/unisb/cs/st/javaslicer/slicing/TestString1.java
 *
 * This file is part of the JavaSlicer tool, developed by Clemens Hammacher at Saarland University.
 * See http://www.st.cs.uni-saarland.de/javaslicer/ for more information.
 *
 * JavaSlicer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JavaSlicer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaSlicer. If not, see <http://www.gnu.org/licenses/>.
 */
package de.unisb.cs.st.javaslicer.slicing;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import de.unisb.cs.st.javaslicer.AbstractSlicingTest;
import de.unisb.cs.st.javaslicer.common.classRepresentation.Instruction;


public class TestString1 extends AbstractSlicingTest {

    @Test
    public void testAll() throws IllegalArgumentException, IOException {
        final List<Instruction> slice = getSlice("/traces/string1", "main", "de.unisb.cs.st.javaslicer.tracedCode.String1.main:*");
        /*
        checkSlice(slice, new String[] {
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 ALOAD 0",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 ICONST_0",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 AALOAD",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 ICONST_0",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 INVOKEVIRTUAL java/lang/String.charAt(I)C",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 BIPUSH 48",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 ISUB",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:29 ISTORE 1",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:30 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:30 ILOAD 1",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:30 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:30 ISTORE 2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:31 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:31 ILOAD 1",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:31 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:31 ISTORE 3",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:32 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:32 ILOAD 3",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:32 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:32 ISTORE 4",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:33 ICONST_2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:33 ILOAD 2",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:33 IMUL",
                "de.unisb.cs.st.javaslicer.tracedCode.Simple2.main:33 ISTORE 5",
            });
        */
    }

}
