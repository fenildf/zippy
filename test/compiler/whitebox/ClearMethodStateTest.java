/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test ClearMethodStateTest
 * @library /testlibrary /testlibrary/whitebox
 * @build ClearMethodStateTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmixed -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ClearMethodStateTest
 * @author igor.ignatyev@oracle.com
 */
public class ClearMethodStateTest extends CompilerWhiteBoxTest {
    public static void main(String[] args) throws Exception {
        // to prevent inlining #method into #compile() and #test()
        WHITE_BOX.testSetDontInlineMethod(METHOD, true);
        new ClearMethodStateTest().runTest();
    }

    protected void test() throws Exception {
        checkNotCompiled(METHOD);
        compile();
        checkCompiled(METHOD);
        WHITE_BOX.clearMethodState(METHOD);
        WHITE_BOX.deoptimizeMethod(METHOD);
        checkNotCompiled(METHOD);


        if (!TIERED_COMPILATION) {
            WHITE_BOX.clearMethodState(METHOD);
            compile(COMPILE_THRESHOLD);
            checkCompiled(METHOD);

            WHITE_BOX.deoptimizeMethod(METHOD);
            checkNotCompiled(METHOD);
            WHITE_BOX.clearMethodState(METHOD);

            if (COMPILE_THRESHOLD > 1) {
                compile(COMPILE_THRESHOLD - 1);
                checkNotCompiled(METHOD);
            } else {
               System.err.println("Warning: 'CompileThreshold' <= 1");
            }

            method();
            checkCompiled(METHOD);
        } else {
            System.err.println(
                    "Warning: part of test is not applicable in Tiered");
        }
    }
}