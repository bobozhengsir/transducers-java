// Copyright 2014 Cognitect. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cognitect.transducers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.cognitect.transducers.Impl.reduce;

public class Fns {

    /**
     * Converts an step function into a complete reducing function. If passed an IReducingFunction, which
     * is an IStepFunction, returns it; otherwise returns a new instance of IReducingFunction with a step
     * function that forwards to the given IStepFunction, a single-arity apply method (used for completing
     * reduction) that returns its argument (it's an identity function), and a zero-arity apply method
     * (used for initializing a new return value for a reduction if one is not provided) that throws an
     * IllegalStateException.
     * @param sf The step function to convert to an IReducingFunction, if it is not one already
     * @param <R> the return type of the step function and reducing function
     * @param <T> the input type of the step function and the reducing function
     * @return a new reducing function, or the input step function if it is already a reducing function
     */
    @SuppressWarnings("unchecked")
    public static <R, T> IReducingFunction<R, ? super T> completing(final IStepFunction<R, ? super T> sf) {
        if (sf instanceof IReducingFunction)
            return (IReducingFunction<R, ? super T>) sf;
        else
            return new AReducingFunction<R, T>() {
                @Override
                public R apply(R result, T input, AtomicBoolean reduced) {
                    return sf.apply(result, input, reduced);
                }
            };
    }

    /**
     * Reduces input using transformed reducing function. Transforms reducing function by applying
     * transducer. Reducing function must implement zero-arity apply that returns initial result
     * to start reducing process.
     * @param xf a transducer (or composed transducers) that transforms the reducing function
     * @param rf a reducing function
     * @param input the input to reduce
     * @param <R> return type
     * @param <A> type of input expected by reducing function
     * @param <B> type of input and type accepted by reducing function returned by transducer
     * @return result of reducing transformed input
     */
    public static <R, A, B> R transduce(ITransducer<A, B> xf, IReducingFunction<R, ? super A> rf, Iterable<B> input) {
        IReducingFunction<R, B> _xf = xf.apply(rf);
        return reduce(_xf, rf.apply(), input);
    }

    /**
     * Reduces input using transformed reducing function. Transforms reducing function by applying
     * transducer. Step function is converted to reducing function if necessary. Accepts initial value
     * for reducing process as argument.
     * @param xf a transducer (or composed transducers) that transforms the reducing function
     * @param rf a reducing function
     * @param init an initial value to start reducing process
     * @param input the input to reduce
     * @param <R> return type
     * @param <A> type expected by reducing function
     * @param <B> type of input and type accepted by reducing function returned by transducer
     * @return result of reducing transformed input
     */
    public static <R, A, B> R transduce(ITransducer<A, B> xf, IStepFunction<R, ? super A> rf, R init, Iterable<B> input) {
        IReducingFunction<R, ? super A> _rf = completing(rf);
        IReducingFunction<R, B> _xf = xf.apply(_rf);
        return reduce(_xf, init, input);
    }

    /**
     * Transduces input into collection using built-in reducing function.
     * @param xf a transducer (or composed transducers) that transforms the reducing function
     * @param init an initial collection to start reducing process
     * @param input the input to put into the collection
     * @param <R> return type
     * @param <A> type the collection contains
     * @param <B> type of input and type accepted by reducing function returned by transducer
     * @return
     */
    public static <R extends Collection<A>, A, B> R into(ITransducer<A, B> xf, R init, Iterable<B> input) {
        return transduce(xf, new AReducingFunction<R, A>() {
            @Override
            public R apply(R result, A input, AtomicBoolean reduced) {
                result.add(input);
                return result;
            }
        }, init, input);
    }

    /**
     * Composes a transducer with another transducer, yielding a new transducer that
     * @param left left hand transducer
     * @param right right hand transducer
     * @param <A> reducing function input type
     * @param <B> reducing function input type
     * @param <C> reducing function input type
     * @return
     */
    public static <A, B, C> ITransducer<A, C> compose(final ITransducer<B, C> left, final ITransducer<A, B> right) {
        return left.comp(right);
    }


    // *** transducers

    /**
     * Creates a transducer that transforms a reducing function by applying a mapping
     * function to each input.
     * @param f a mapping function from one type to another (can be the same type)
     * @param <A> input type of input reducing function
     * @param <B> input type of output reducing function
     * @return a new transducer
     */
    public static <A, B> ITransducer<A, B> map(final Function<B, A> f) {
        return new ITransducer<A, B>() {
            @Override
            public <R> IReducingFunction<R, B> apply(final IReducingFunction<R,? super A> rf) {
                return new AReducingFunctionOn<R, A, B>(rf) {
                    @Override
                    public R apply(R result, B input, AtomicBoolean reduced) {
                        return rf.apply(result, f.apply(input), reduced);
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function by applying a
     * predicate to each input and processing only those inputs for which the
     * predicate is true.
     * @param p a predicate function
     * @param <A> input type of input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> filter(final Predicate<A> p) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        if (p.test(input))
                            return rf.apply(result, input, reduced);
                        return result;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function by accepting
     * an iterable of the expected input type and reducing it
     * @param <A> input type of input reducing function
     * @param <B> input type of output reducing function
     * @return a new transducer
     */
    public static <A, B extends Iterable<A>> ITransducer<A, B> cat() {
        return new ITransducer<A, B>() {
            @Override
            public <R> IReducingFunction<R, B> apply(final IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, B>(rf) {
                    @Override
                    public R apply(R result, B input, AtomicBoolean reduced) {
                        return reduce(rf, result, input, reduced);
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function using
     * a composition of map and cat.
     * @param f a mapping function from one type to another (can be the same type)
     * @param <A> input type of input reducing function
     * @param <B> output type of output reducing function and iterable of input type
     *           of input reducing function
     * @param <C> input type of output reducing function
     * @return a new transducer
     */
    public static <A, B extends Iterable<A>, C> ITransducer<A, C> mapcat(Function<C, B> f) {
        return map(f).comp(Fns.<A, B>cat());
    }

    /**
     * Creates a transducer that transforms a reducing function by applying a
     * predicate to each input and not processing those inputs for which the
     * predicate is true.
     * @param p a predicate function
     * @param <A> input type of input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> remove(final Predicate<A> p) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        if (!p.test(input))
                            return rf.apply(result, input, reduced);
                        return result;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * it only processes n inputs, then the reducing process stops.
     * @param n the number of inputs to process
     * @param <A> input type of input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> take(final long n) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    long taken = 0;
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        R ret = result;
                        if (taken < n) {
                            ret = rf.apply(result, input, reduced);
                            taken++;
                        } else {
                            reduced.set(true);
                        }
                        return ret;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * it processes inputs as long as the provided predicate returns true.
     * If the predicate returns false, the reducing process stops.
     * @param p a predicate used to test inputs
     * @param <A> input type of input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> takeWhile(final Predicate<A> p) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        R ret = result;
                        if (p.test(input)) {
                            ret = rf.apply(result, input, reduced);
                        } else {
                            reduced.set(true);
                        }
                        return ret;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * it skips n inputs, then processes the rest of the inputs.
     * @param n the number of inputs to skip
     * @param <A> input type of input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> drop(final long n) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R,? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    long dropped = 0;
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        R ret = result;
                        if (dropped < n) {
                            dropped++;
                        } else {
                            ret = rf.apply(result, input, reduced);
                        }
                        return ret;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * it skips inputs as long as the provided predicate returns true.
     * Once the predicate returns false, the rest of the inputs are
     * processed.
     * @param p a predicate used to test inputs
     * @param <A> input type of input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> dropWhile(final Predicate<A> p) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    boolean drop = true;
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        if (drop && p.test(input)) {
                            return result;
                        }
                        drop = false;
                        return rf.apply(result, input, reduced);
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * it processes every nth input.
     * @param n The frequence of inputs to process (e.g., 3 processes every third input).
     * @param <A> The input type of the input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> takeNth(final long n) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    long nth = 0;
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        return ((nth++ % n) == 0) ? rf.apply(result, input, reduced) : result;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * inputs that are keys in the provided map are replaced by the corresponding
     * value in the map.
     * @param smap a map of replacement values
     * @param <A> the input type of the input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> replace(final Map<A, A> smap) {
        return map(new Function<A, A>() {
            @Override
            public A apply(A a) {
                if (smap.containsKey(a))
                    return smap.get(a);
                return a;
            }
        });
    }

    /**
     * Creates a transducer that transforms a reducing function by applying a
     * function to each input and processing the resulting value, ignoring values
     * that are null.
     * @param f a function for processing inputs
     * @param <A> the input type of the input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> keep(final Function<A, A> f) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        A _input = f.apply(input);
                        if (_input != null)
                            return rf.apply(result, _input, reduced);
                        return result;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function by applying a
     * function to each input and processing the resulting value, ignoring values
     * that are null.
     * @param f a function for processing inputs
     * @param <A> the input type of the input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> keepIndexed(final BiFunction<Long, A, A> f) {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    long n = 0;
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        n++;
                        A _input = f.apply(n, input);
                        if (_input != null)
                            return rf.apply(result, _input, reduced);
                        return result;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * consecutive identical input values are removed, only a single value
     * is processed.
     * @param <A> the input type of the input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> dedupe() {
        return new ITransducer<A, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(IReducingFunction<R, ? super A> rf) {
                return new AReducingFunctionOn<R, A, A>(rf) {
                    A prior = null;
                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        R ret = result;
                        if (prior != input) {
                            prior = input;
                            ret = rf.apply(result, input, reduced);
                        }
                        return ret;
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function such that
     * it has the specified probability of processing each input.
     * @param prob the probability between expressed as a value between 0 and 1.
     * @param <A> the input type of the input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<A, A> randomSample(final Double prob) {
        return filter(a ->  Math.random() < prob);
    }

    /**
     * Creates a transducer that transforms a reducing function that processes
     * iterables of input into a reducing function that processes individual inputs
     * by gathering series of inputs for which the provided partitioning function returns
     * the same value, only forwarding them to the next reducing function when the value
     * the partitioning function returns for a given input is different from the value
     * returned for the previous input.
     * @param f the partitioning function
     * @param <A> the input type of the input and output reducing functions
     * @param <P> the type returned by the partitioning function
     * @return a new transducer
     */
    public static <A, P> ITransducer<Iterable<A>, A> partitionBy(final Function<A, P> f) {
        return new ITransducer<Iterable<A>, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(final IReducingFunction<R, ? super Iterable<A>> rf) {
                return new IReducingFunction<R, A>() {
                    List<A> part = new ArrayList<A>();
                    Object mark = new Object();
                    Object prior = mark;

                    @Override
                    public R apply() {
                        return rf.apply();
                    }

                    @Override
                    public R apply(R result) {
                        R ret = result;
                        if (!part.isEmpty()) {
                            List<A> copy = new ArrayList<A>(part);
                            part.clear();
                            ret = rf.apply(result, copy, new AtomicBoolean());
                        }
                        return rf.apply(ret);
                    }

                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        P val = f.apply(input);
                        if ((prior == mark) || (prior.equals(val))) {
                            prior = val;
                            part.add(input);
                            return result;
                        } else {
                            List<A> copy = new ArrayList<A>(part);
                            prior = val;
                            part.clear();
                            R ret = rf.apply(result, copy, reduced);
                            if (!reduced.get()) {
                                part.add(input);
                            }
                            return ret;
                        }
                    }
                };
            }
        };
    }

    /**
     * Creates a transducer that transforms a reducing function that processes
     * iterables of input into a reducing function that processes individual inputs
     * by gathering series of inputs into partitions of a given size, only forwarding
     * them to the next reducing function when enough inputs have been accrued. Processes
     * any remaining buffered inputs when the reducing process completes.
     * @param n the size of each partition
     * @param <A> the input type of the input and output reducing functions
     * @return a new transducer
     */
    public static <A> ITransducer<Iterable<A>, A> partitionAll(final int n) {

        return new ITransducer<Iterable<A>, A>() {
            @Override
            public <R> IReducingFunction<R, A> apply(final IReducingFunction<R, ? super Iterable<A>> rf) {
                return new IReducingFunction<R, A>() {
                    List<A> part = new ArrayList<A>(n);

                    @Override
                    public R apply() {
                        return rf.apply();
                    }

                    @Override
                    public R apply(R result) {
                        R ret = result;
                        if (!part.isEmpty()) {
                            List<A> copy = new ArrayList<A>(part);
                            part.clear();
                            ret = rf.apply(result, copy, new AtomicBoolean());
                        }
                        return rf.apply(ret);
                    }

                    @Override
                    public R apply(R result, A input, AtomicBoolean reduced) {
                        part.add(input);
                        if (n == part.size()) {
                            List<A> copy = new ArrayList<A>(part);
                            part.clear();
                            return rf.apply(result, copy, reduced);
                        }
                        return result;
                    }
                };
            }
        };
    }
}
