/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl;

import java.io.*;
import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.builtins.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.call.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.expression.*;
import com.oracle.truffle.sl.nodes.local.*;
import com.oracle.truffle.sl.parser.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * SL is a simple language to demonstrate and showcase features of Truffle. The implementation is as
 * simple and clean as possible in order to help understanding the ideas and concepts of Truffle.
 * The language has first class functions, but no object model.
 * <p>
 * SL is dynamically typed, i.e., there are no type names specified by the programmer. SL is
 * strongly typed, i.e., there is no automatic conversion between types. If an operation is not
 * available for the types encountered at run time, a type error is reported and execution is
 * stopped. For example, {@code 4 - "2"} results in a type error because subtraction is only defined
 * for numbers.
 * 
 * <p>
 * <b>Types:</b>
 * <ul>
 * <li>Number: arbitrary precision integer numbers. The implementation uses the Java primitive type
 * {@code long} to represent numbers that fit into the 64 bit range, and {@link BigInteger} for
 * numbers that exceed the range. Using a primitive type such as {@code long} is crucial for
 * performance.
 * <li>Boolean: implemented as the Java primitive type {@code boolean}.
 * <li>String: implemented as the Java standard type {@link String}.
 * <li>Function: implementation type {@link SLFunction}.
 * <li>Null (with only one value {@code null}): implemented as the singleton
 * {@link SLNull#SINGLETON}.
 * </ul>
 * The class {@link SLTypes} lists these types for the Truffle DSL, i.e., for type-specialized
 * operations that are specified using Truffle DSL annotations.
 * 
 * <p>
 * <b>Language concepts:</b>
 * <ul>
 * <li>Literals for {@link SLBigIntegerLiteralNode numbers} , {@link SLStringLiteralNode strings},
 * and {@link SLFunctionLiteralNode functions}.
 * <li>Basic arithmetic, logical, and comparison operations: {@link SLAddNode +}, {@link SLSubNode
 * -}, {@link SLMulNode *}, {@link SLDivNode /}, {@link SLLogicalAndNode logical and},
 * {@link SLLogicalOrNode logical or}, {@link SLEqualNode ==}, !=, {@link SLLessThanNode <},
 * {@link SLLessOrEqualNode <=}, >, >=.
 * <li>Local variables: local variables must be defined (via a {@link SLWriteLocalVariableNode
 * write}) before they can be used (by a {@link SLReadLocalVariableNode read}). Local variables are
 * not visible outside of the block where they were first defined.
 * <li>Basic control flow statements: {@link SLBlockNode blocks}, {@link SLIfNode if},
 * {@link SLWhileNode while} with {@link SLBreakNode break} and {@link SLContinueNode continue},
 * {@link SLReturnNode return}.
 * <li>Function calls: {@link SLCallNode calls} are efficiently implemented with
 * {@link SLAbstractDispatchNode polymorphic inline caches}.
 * </ul>
 * 
 * <p>
 * <b>Syntax and parsing:</b><br>
 * The syntax is described as an attributed grammar. The {@link Parser} and {@link Scanner} are
 * automatically generated by the parser generator Coco/R (available from <a
 * href="http://ssw.jku.at/coco/">http://ssw.jku.at/coco/</a>). The grammar contains semantic
 * actions that build the AST for a method. To keep these semantic actions short, they are mostly
 * calls to the {@link SLNodeFactory} that performs the actual node creation. All functions found in
 * the SL source are added to the {@link SLFunctionRegistry}, which is accessible from the
 * {@link SLContext}.
 * 
 * <p>
 * <b>Builtin functions:</b><br>
 * Library functions that are available to every SL source without prior definition are called
 * builtin functions. They are added to the {@link SLFunctionRegistry} when the {@link SLContext} is
 * created. There current builtin functions are
 * <ul>
 * <li>{@link SLReadlnBuiltin readln}: Read a String from the {@link SLContext#getInput() standard
 * input}.
 * <li>{@link SLPrintlnBuiltin println}: Write a value to the {@link SLContext#getOutput() standard
 * output}.
 * <li>{@link SLNanoTimeBuiltin nanoTime}: Returns the value of a high-resolution time, in
 * nanoseconds.
 * <li>{@link SLDefineFunctionBuiltin defineFunction}: Parses the functions provided as a String
 * argument and adds them to the function registry. Functions that are already defined are replaced
 * with the new version.
 * </ul>
 */
public class SLMain {

    /**
     * The main entry point. Use the mx command "mx sl" to run it with the correct class path setup.
     */
    public static void main(String[] args) throws IOException {
        SourceManager sourceManager = new SourceManager();

        Source source;
        if (args.length == 0) {
            source = sourceManager.get("stdin", System.in);
        } else {
            source = sourceManager.get(args[0]);
        }

        int repeats = 1;
        if (args.length >= 2) {
            repeats = Integer.parseInt(args[1]);
        }

        SLContext context = new SLContext(sourceManager, new BufferedReader(new InputStreamReader(System.in)), System.out);
        run(context, source, System.out, repeats);
    }

    /**
     * Parse and run the specified SL source. Factored out in a separate method so that it can also
     * be used by the unit test harness.
     */
    public static void run(SLContext context, Source source, PrintStream logOutput, int repeats) {
        if (logOutput != null) {
            logOutput.println("== running on " + Truffle.getRuntime().getName());
        }

        /* Parse the SL source file. */
        Parser.parseSL(context, source);
        /* Lookup our main entry point, which is per definition always named "main". */
        SLFunction main = context.getFunctionRegistry().lookup("main");
        if (main.getCallTarget() == null) {
            throw new SLException("No function main() defined in SL source file.");
        }

        /* Change to true if you want to see the AST on the console. */
        boolean printASTToLog = false;
        /* Change to dump the AST to IGV over the network. */
        boolean dumpASTToIGV = false;

        printScript("before execution", context, logOutput, printASTToLog, dumpASTToIGV);
        try {
            for (int i = 0; i < repeats; i++) {
                long start = System.nanoTime();
                /* Call the main entry point, without any arguments. */
                try {
                    Object result = main.getCallTarget().call(null, new SLArguments(new Object[0]));
                    if (result != SLNull.SINGLETON) {
                        context.getOutput().println(result);
                    }
                } catch (UnsupportedSpecializationException ex) {
                    context.getOutput().println(formatTypeError(ex));
                }
                long end = System.nanoTime();

                if (logOutput != null && repeats > 1) {
                    logOutput.println("== iteration " + (i + 1) + ": " + ((end - start) / 1000000) + " ms");
                }
            }

        } finally {
            printScript("after execution", context, logOutput, printASTToLog, dumpASTToIGV);
        }
    }

    /**
     * Dumps the AST of all functions to the IGV visualizer, via a socket connection. IGV can be
     * started with the mx command "mx igv". Optionally, also prints the ASTs to the console.
     */
    private static void printScript(String groupName, SLContext context, PrintStream logOutput, boolean printASTToLog, boolean dumpASTToIGV) {
        if (dumpASTToIGV) {
            GraphPrintVisitor graphPrinter = new GraphPrintVisitor();
            graphPrinter.beginGroup(groupName);
            for (SLFunction function : context.getFunctionRegistry().getFunctions()) {
                RootCallTarget callTarget = function.getCallTarget();
                if (callTarget != null) {
                    graphPrinter.beginGraph(function.toString()).visit(callTarget.getRootNode());
                }
            }
            graphPrinter.printToNetwork(true);
        }
        if (printASTToLog && logOutput != null) {
            for (SLFunction function : context.getFunctionRegistry().getFunctions()) {
                RootCallTarget callTarget = function.getCallTarget();
                if (callTarget != null) {
                    logOutput.println("=== " + function);
                    NodeUtil.printTree(logOutput, callTarget.getRootNode());
                }
            }
        }
    }

    /**
     * Provides a user-readable message for run-time type errors. SL is strongly typed, i.e., there
     * are no automatic type conversions of values. Therefore, Truffle does the type checking for
     * us: if no matching node specialization for the actual values is found, then we have a type
     * error. Specialized nodes use the {@link UnsupportedSpecializationException} to report that no
     * specialization was found. We therefore just have to convert the information encapsulated in
     * this exception in a user-readable form.
     */
    private static String formatTypeError(UnsupportedSpecializationException ex) {
        StringBuilder result = new StringBuilder();
        result.append("Type error");
        if (ex.getNode() != null && ex.getNode().getSourceSection() != null) {
            SourceSection ss = ex.getNode().getSourceSection();
            result.append(" at ").append(ss.getSource().getName()).append(" line ").append(ss.getStartLine()).append(" col ").append(ss.getStartColumn());
        }
        result.append(": operation");
        if (ex.getNode() != null && ex.getNode().getClass().getAnnotation(NodeInfo.class) != null) {
            result.append(" \"").append(ex.getNode().getClass().getAnnotation(NodeInfo.class).shortName()).append("\"");
        }
        result.append(" not defined for");

        String sep = " ";
        for (int i = 0; i < ex.getSuppliedValues().length; i++) {
            Object value = ex.getSuppliedValues()[i];
            Node node = ex.getSuppliedNodes()[i];
            if (node != null) {
                result.append(sep);
                sep = ", ";

                if (value instanceof Long || value instanceof BigInteger) {
                    result.append("Number ").append(value);
                } else if (value instanceof Boolean) {
                    result.append("Boolean ").append(value);
                } else if (value instanceof String) {
                    result.append("String \"").append(value).append("\"");
                } else if (value instanceof SLFunction) {
                    result.append("Function ").append(value);
                } else if (value == SLNull.SINGLETON) {
                    result.append("NULL");
                } else if (value == null) {
                    // value is not evaluated because of short circuit evaluation
                    result.append("ANY");
                } else {
                    result.append(value);
                }
            }
        }
        return result.toString();
    }
}
