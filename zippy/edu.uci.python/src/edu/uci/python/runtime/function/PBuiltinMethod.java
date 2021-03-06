/*
 * Copyright (c) 2013, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uci.python.runtime.function;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

import edu.uci.python.runtime.standardtype.*;

public class PBuiltinMethod extends PythonBuiltinObject implements PythonCallable {

    private final PBuiltinFunction function;
    private final PythonBuiltinObject self;
    private final RootCallTarget callTarget;

    public PBuiltinMethod(PythonBuiltinObject self, PBuiltinFunction function) {
        this.self = self;
        this.function = function;
        this.callTarget = function.getCallTarget();
    }

    public PBuiltinFunction __func__() {
        return function;
    }

    public PythonBuiltinObject __self__() {
        return self;
    }

    /**
     * There is no declaration frame for built-in methods.
     */
    public Object call(Object[] arguments) {
        PArguments.insertSelf(arguments, self);
        return callTarget.call(arguments);
    }

    public Object call(Object[] args, PKeyword[] keywords) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return function.getFrameDescriptor();
    }

    @Override
    public void arityCheck(int numOfArgs, int numOfKeywords, String[] keywords) {
        function.arityCheck(numOfArgs, numOfKeywords, keywords);
    }

    @Override
    public Arity getArity() {
        return function.getArity();
    }

    @Override
    public String getName() {
        return function.getName();
    }

    @Override
    public String toString() {
        return "<method '" + function.getName() + "' of '" + self + "' objects>";
    }

}
