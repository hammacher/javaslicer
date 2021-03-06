/** License information:
 *    Component: 
 *    Package:   
 *    Class:     
 *    Filename:  evaluation/method1/Method1.java
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
public class Method1 {

    public static void main(String[] args) {
        int a = args[0].charAt(0)-'0'; // this expression must not be constant!
        int b = args[0].charAt(0)-'0'; // this expression must not be constant!
        int c = getFirst(a, b);
        ++c;
    }

    private static int getFirst(int first, int second) {
        return first;
    }

}

