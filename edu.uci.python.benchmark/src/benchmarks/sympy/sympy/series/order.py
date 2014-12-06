from __future__ import print_function, division

from sympy.core import Basic, S, sympify, Expr, Rational, Symbol, Dummy
from sympy.core import Add, Mul, expand_power_base, expand_log
from sympy.core.cache import cacheit
from sympy.core.compatibility import default_sort_key, is_sequence
from sympy.core.containers import Tuple
from sympy.utilities.iterables import uniq


class Order(Expr):
    r""" Represents the limiting behavior of some function

    The order of a function characterizes the function based on the limiting
    behavior of the function as it goes to some limit. Only taking all limit
    points to be 0 or positive infinity is currently supported. This is
    expressed in big O notation [1]_.

    The formal definition for the order of a function `g(x)` about a point `a`
    is such that `g(x) = O(f(x))` as `x \rightarrow a` if and only if for any
    `\delta > 0` there exists a `M > 0` such that `|g(x)| \leq M|f(x)|` for
    `|x-a| < \delta`.  This is equivalent to `\lim_{x \rightarrow a}
    \sup |g(x)/f(x)| < \infty`.

    Let's illustrate it on the following example by taking the expansion of
    `\sin(x)` about 0:

    .. math ::
        \sin(x) = x - x^3/3! + O(x^5)

    where in this case `O(x^5) = x^5/5! - x^7/7! + \cdots`. By the definition
    of `O`, for any `\delta > 0` there is an `M` such that:

    .. math ::
        |x^5/5! - x^7/7! + ....| <= M|x^5| \text{ for } |x| < \delta

    or by the alternate definition:

    .. math ::
        \lim_{x \rightarrow 0} | (x^5/5! - x^7/7! + ....) / x^5| < \infty

    which surely is true, because

    .. math ::
        \lim_{x \rightarrow 0} | (x^5/5! - x^7/7! + ....) / x^5| = 1/5!


    As it is usually used, the order of a function can be intuitively thought
    of representing all terms of powers greater than the one specified. For
    example, `O(x^3)` corresponds to any terms proportional to `x^3,
    x^4,\ldots` and any higher power. For a polynomial, this leaves terms
    proportional to `x^2`, `x` and constants.

    Examples
    ========

    >>> from sympy import O, oo
    >>> from sympy.abc import x, y

    >>> O(x + x**2)
    O(x)
    >>> O(x + x**2, (x, 0))
    O(x)
    >>> O(x + x**2, (x, oo))
    O(x**2, (x, oo))

    >>> O(1 + x*y)
    O(1, x, y)
    >>> O(1 + x*y, (x, 0), (y, 0))
    O(1, x, y)
    >>> O(1 + x*y, (x, oo), (y, oo))
    O(x*y, (x, oo), (y, oo))

    >>> O(1) in O(1, x)
    True
    >>> O(1, x) in O(1)
    False
    >>> O(x) in O(1, x)
    True
    >>> O(x**2) in O(x)
    True

    >>> O(x)*x
    O(x**2)
    >>> O(x) - O(x)
    O(x)

    References
    ==========

    .. [1] `Big O notation <http://en.wikipedia.org/wiki/Big_O_notation>`_

    Notes
    =====

    In ``O(f(x), x)`` the expression ``f(x)`` is assumed to have a leading
    term.  ``O(f(x), x)`` is automatically transformed to
    ``O(f(x).as_leading_term(x),x)``.

        ``O(expr*f(x), x)`` is ``O(f(x), x)``

        ``O(expr, x)`` is ``O(1)``

        ``O(0, x)`` is 0.

    Multivariate O is also supported:

        ``O(f(x, y), x, y)`` is transformed to
        ``O(f(x, y).as_leading_term(x,y).as_leading_term(y), x, y)``

    In the multivariate case, it is assumed the limits w.r.t. the various
    symbols commute.

    If no symbols are passed then all symbols in the expression are used.

    """

    is_Order = True

    __slots__ = []

    @cacheit
    def __new__(cls, expr, *args, **kwargs):
        expr = sympify(expr)

        if not args:
            if expr.is_Order:
                variables = expr.variables
                point = expr.point
            else:
                variables = list(expr.free_symbols)
                point = [S.Zero]*len(variables)
        else:
            args = list(args if is_sequence(args) else [args])
            variables, point = [], []
            if is_sequence(args[0]):
                for a in args:
                    v, p = list(map(sympify, a))
                    variables.append(v)
                    point.append(p)
            else:
                variables = list(map(sympify, args))
                point = [S.Zero]*len(variables)

        if not all(isinstance(v, Symbol) for v in variables):
           raise TypeError('Variables are not symbols, got %s' % variables)

        if len(list(uniq(variables))) != len(variables):
            raise ValueError('Variables are supposed to be unique symbols, got %s' % variables)

        if expr.is_Order:
            expr_vp = dict(expr.args[1:])
            new_vp = dict(expr_vp)
            vp = dict(zip(variables, point))
            for v, p in vp.items():
                if v in new_vp.keys():
                    if p != new_vp[v]:
                        raise NotImplementedError(
                            "Mixing Order at different points is not supported.")
                else:
                    new_vp[v] = p
            if set(expr_vp.keys()) == set(new_vp.keys()):
                return expr
            else:
                variables = list(new_vp.keys())
                point = [new_vp[v] for v in variables]

        if expr is S.NaN:
            return S.NaN

        if not all(p is S.Zero for p in point) and \
           not all(p is S.Infinity for p in point):
            raise NotImplementedError('Order at points other than 0 '
                'or oo not supported, got %s as a point.' % point)

        if variables:
            if len(variables) > 1:
                # XXX: better way?  We need this expand() to
                # workaround e.g: expr = x*(x + y).
                # (x*(x + y)).as_leading_term(x, y) currently returns
                # x*y (wrong order term!).  That's why we want to deal with
                # expand()'ed expr (handled in "if expr.is_Add" branch below).
                expr = expr.expand()

            if expr.is_Add:
                lst = expr.extract_leading_order(variables, point)
                expr = Add(*[f.expr for (e, f) in lst])

            elif expr:
                if point[0] == S.Zero:
                    expr = expr.as_leading_term(*variables)
                expr = expr.as_independent(*variables, as_Add=False)[1]

                expr = expand_power_base(expr)
                expr = expand_log(expr)

                if len(variables) == 1:
                    # The definition of O(f(x)) symbol explicitly stated that
                    # the argument of f(x) is irrelevant.  That's why we can
                    # combine some power exponents (only "on top" of the
                    # expression tree for f(x)), e.g.:
                    # x**p * (-x)**q -> x**(p+q) for real p, q.
                    x = variables[0]
                    margs = list(Mul.make_args(
                        expr.as_independent(x, as_Add=False)[1]))

                    for i, t in enumerate(margs):
                        if t.is_Pow:
                            b, q = t.args
                            if b in (x, -x) and q.is_real and not q.has(x):
                                margs[i] = x**q
                            elif b.is_Pow and not b.exp.has(x):
                                b, r = b.args
                                if b in (x, -x) and r.is_real:
                                    margs[i] = x**(r*q)
                            elif b.is_Mul and b.args[0] is S.NegativeOne:
                                b = -b
                                if b.is_Pow and not b.exp.has(x):
                                    b, r = b.args
                                    if b in (x, -x) and r.is_real:
                                        margs[i] = x**(r*q)

                    expr = Mul(*margs)

        if expr is S.Zero:
            return expr

        if expr.is_Order:
            expr = expr.expr

        if not expr.has(*variables):
            expr = S.One

        # create Order instance:
        variables.sort(key=default_sort_key)
        args = (expr,) + Tuple(*zip(variables, point))
        obj = Expr.__new__(cls, *args)
        return obj

    def _eval_nseries(self, x, n, logx):
        return self

    @property
    def expr(self):
        return self.args[0]

    @property
    def variables(self):
        if self.args[1:]:
            return tuple(x[0] for x in self.args[1:])
        else:
            return ()

    @property
    def point(self):
        if self.args[1:]:
            return tuple(x[1] for x in self.args[1:])
        else:
            return ()

    @property
    def free_symbols(self):
        return self.expr.free_symbols | set(self.variables)

    def _eval_power(b, e):
        if e.is_Number and e.is_nonnegative:
            return b.func(b.expr ** e, *b.args[1:])
        return

    def as_expr_variables(self, order_symbols):
        if order_symbols is None:
            order_symbols = self.args[1:]
        else:
            if not all(o[1] == order_symbols[0][1] for o in order_symbols) and \
               not all(p == self.point[0] for p in self.point):
                raise NotImplementedError('Order at points other than 0 '
                    'or oo not supported, got %s as a point.' % point)
            if order_symbols[0][1] != self.point[0]:
                raise NotImplementedError(
                        "Multiplying Order at different points is not supported.")
            order_symbols = dict(order_symbols)
            for s, p in dict(self.args[1:]).items():
                if s not in order_symbols.keys():
                    order_symbols[s] = p
            order_symbols = sorted(order_symbols.items(), key=lambda x: default_sort_key(x[0]))
        return self.expr, tuple(order_symbols)

    def removeO(self):
        return S.Zero

    def getO(self):
        return self

    @cacheit
    def contains(self, expr):
        """
        Return True if expr belongs to Order(self.expr, \*self.variables).
        Return False if self belongs to expr.
        Return None if the inclusion relation cannot be determined
        (e.g. when self and expr have different symbols).
        """
        from sympy import powsimp, PoleError
        if expr is S.Zero:
            return True
        if expr is S.NaN:
            return False
        if expr.is_Order:
            if not all(p == expr.point[0] for p in expr.point) and \
               not all(p == self.point[0] for p in self.point):
                raise NotImplementedError('Order at points other than 0 '
                    'or oo not supported, got %s as a point.' % point)
            else:
                # self and/or expr is O(1):
                if any(not p for p in [expr.point, self.point]):
                    point = self.point + expr.point
                    if point:
                        point = point[0]
                    else:
                        point = S.Zero
                else:
                    point = self.point[0]
            if expr.expr == self.expr:
                # O(1) + O(1), O(1) + O(1, x), etc.
                return all([x in self.args[1:] for x in expr.args[1:]])
            if expr.expr.is_Add:
                return all([self.contains(x) for x in expr.expr.args])
            if self.expr.is_Add:
                return any([self.func(x, *self.args[1:]).contains(expr)
                            for x in self.expr.args])
            if self.variables and expr.variables:
                common_symbols = tuple(
                    [s for s in self.variables if s in expr.variables])
            elif self.variables:
                common_symbols = self.variables
            else:
                common_symbols = expr.variables
            if not common_symbols:
                return None
            r = None
            ratio = self.expr/expr.expr
            ratio = powsimp(ratio, deep=True, combine='exp')
            for s in common_symbols:
                try:
                    l = ratio.limit(s, point) != 0
                except PoleError:
                    l = None
                if r is None:
                    r = l
                else:
                    if r != l:
                        return
            return r
        obj = self.func(expr, *self.args[1:])
        return self.contains(obj)

    def __contains__(self, other):
        result = self.contains(other)
        if result is None:
            raise TypeError('contains did not evaluate to a bool')
        return result

    def _eval_subs(self, old, new):
        if old.is_Symbol and old in self.variables:
            i = self.variables.index(old)
            newexpr = self.expr._subs(old, new)
            if isinstance(new, Symbol):
                newvars = list(self.variables)
                newvars[i] = new
                newpt = self.point
            else:
                newvars = tuple(newexpr.free_symbols) + \
                    self.variables[:i] + self.variables[i + 1:]
                p = new.as_numer_denom()[1].is_number*2 - 1
                newpt = self.point[0]**p
                if not newpt.is_real:
                    x = Dummy('x')
                    newpt = (x**p).limit(x, self.point[0])
                newpt = [newpt]*len(newvars)
            return Order(newexpr, *zip(newvars, newpt))
        return Order(self.expr._subs(old, new), *self.args[1:])

    def _eval_conjugate(self):
        expr = self.expr._eval_conjugate()
        if expr is not None:
            return self.func(expr, *self.args[1:])

    def _eval_derivative(self, x):
        return self.func(self.expr.diff(x), *self.args[1:]) or self

    def _eval_transpose(self):
        expr = self.expr._eval_transpose()
        if expr is not None:
            return self.func(expr, *self.args[1:])

    def _sage_(self):
        #XXX: SAGE doesn't have Order yet. Let's return 0 instead.
        return Rational(0)._sage_()

O = Order