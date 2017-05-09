package com.cognitect.transducers;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;
import java.util.function.Function;

import static com.cognitect.transducers.Fns.*;

public class FnsTest {

    private List<Integer> ints(final int n) {
        return new ArrayList<Integer>(n) {{
            for(int i = 0; i < n; i++) {
                add(i);
            }
        }};
    }

    private List<Long> longs(final long n) {
        return new ArrayList<Long>((int)n) {{
            for(long i = 0L; i < n; i++) {
                add(i);
            }
        }};
    }

    private static ITransducer<String, Long> stringify = map(x->x.toString());

    private static IStepFunction<List<String>, String> addString = (x, y, z) -> {x.add(y);return x;};

    @Test
    public void testMap() throws Exception {
        ITransducer<String, Integer> xf = map(x->x.toString());
        IStepFunction<String, String> tmp = (x, y, z) -> x + y + " ";

        String s = transduce(xf, tmp, "", ints(10));

        assertEquals(s, "0 1 2 3 4 5 6 7 8 9 ");

        ITransducer<Integer, Integer> xn = map(Function.identity());

        List<Integer> nums = transduce(xn, (x, y, z) -> {
            x.add(y + 1);
            return x;
        }, new ArrayList<>(), ints(10));

        Integer[] expected = {1,2,3,4,5,6,7,8,9,10};

        assertTrue(nums.equals(Arrays.asList(expected)));

        // README usage test
        List<String> sl = transduce(stringify, addString, new ArrayList<>(), longs(10));

        String[] lexpected = {"0","1","2","3","4","5","6","7","8","9"};

        assertTrue(sl.equals(Arrays.asList(lexpected)));

    }

    @Test
    public void testFilter() throws Exception {

        ITransducer<Integer, Integer> xf = filter(x -> x % 2 != 0);

        List<Integer> odds = transduce(xf,  (x, y, z) -> {
            x.add(y);
            return x;
        }, new ArrayList<>(), ints(10));

        Integer[] expected = {1,3,5,7,9};

        assertTrue(odds.equals(Arrays.asList(expected)));
    }

    @Test
    public void testCat() throws Exception {
        ITransducer<Integer, Iterable<Integer>> xf = cat();
        List<Iterable<Integer>> data = new ArrayList<Iterable<Integer>>() {{
            add(ints(10));
            add(ints(20));
        }};

        List<Integer> vals = transduce(xf, (x, y, z) -> {
            x.add(y);
            return x;
        }, new ArrayList<>(), data);

        int i=0;
        List<Integer> nums = ints(10);

        for(int j=0; j<nums.size(); j++) {
            assertEquals((int)nums.get(j), (int)vals.get(i));
            i += 1;
        }

        nums = ints(20);

        for(int j=0; j<nums.size(); j++) {
            assertEquals((int)nums.get(j), (int)vals.get(i));
            i += 1;
        }
    }

    @Test
    public void testMapcat() throws Exception {
        ITransducer<Character, Integer> xf = mapcat(integer -> {
            final String s = integer.toString();
            return new ArrayList<Character>(s.length()) {{
                for (char c : s.toCharArray())
                    add(c);
            }};
        });

        List<Character> vals = transduce(xf, (x, y, z) -> {
            x.add(y);
            return x;
        }, new ArrayList<>(), ints(10));

        Character[] expected = {'0','1','2','3','4','5','6','7','8','9'};

        assertTrue(vals.equals(Arrays.asList(expected)));
    }

    @Test
    public void testComp() throws Exception {
        ITransducer<Integer, Integer> f = filter(i -> i.intValue() % 2 != 0);

        ITransducer<String, Integer> m = map(i -> i.toString());

        ITransducer<String, Integer> xf = f.comp(m);

        List<String> odds = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), ints(10));

        String[] expected = {"1","3","5","7","9"};

        assertTrue(odds.equals(Arrays.asList(expected)));

        // README Usage tests

        ITransducer<Long, Long> filterOdds = filter(num -> num.longValue() % 2 != 0);

        ITransducer<String, Long> stringifyOdds = filterOdds.comp(stringify);

        List<String> sl = transduce(stringifyOdds, addString, new ArrayList<>(), longs(10));

        String[] lexpected = {"1","3","5","7","9"};

        assertTrue(sl.equals(Arrays.asList(lexpected)));

    }

    @Test
    public void testTake() throws Exception {
        ITransducer<Integer, Integer> xf = take(5);
        List<Integer> five = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), ints(20));

        Integer[] expected = {0,1,2,3,4};

        assertTrue(five.equals(Arrays.asList(expected)));
    }

    @Test
    public void testTakeWhile() throws Exception {
        ITransducer<Integer, Integer> xf = takeWhile(i -> i < 10);
        List<Integer> ten = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), ints(20));

        Integer[] expected = {0,1,2,3,4,5,6,7,8,9};

        assertTrue(ten.equals(Arrays.asList(expected)));
    }

    @Test
    public void testDrop() throws Exception {
        ITransducer<Integer, Integer> xf = drop(5);
        List<Integer> five = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), ints(10));

        Integer[] expected = {5,6,7,8,9};

        assertTrue(five.equals(Arrays.asList(expected)));
    }

    @Test
    public void testDropWhile() throws Exception {
        ITransducer<Integer, Integer> xf = dropWhile(i -> i < 10);
        List<Integer> ten = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<Integer>(), ints(20));

        Integer[] expected = {10,11,12,13,14,15,16,17,18,19};

        assertTrue(ten.equals(Arrays.asList(expected)));
    }

    @Test
    public void testTakeNth() throws Exception {
        ITransducer<Integer, Integer> xf = takeNth(2);
        List<Integer> evens = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), ints(10));

        Integer[] expected = {0,2,4,6,8};

        assertTrue(evens.equals(Arrays.asList(expected)));
    }

    @Test
    public void testReplace() throws Exception {
        ITransducer<Integer, Integer> xf = replace(new HashMap<Integer, Integer>() {{ put(3, 42); }});
        List<Integer> evens = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), ints(5));

        Integer[] expected = {0,1,2,42,4};

        assertTrue(evens.equals(Arrays.asList(expected)));
    }

    @Test
    public void testKeep() throws Exception {
        ITransducer<Integer, Integer> xf = keep(i -> (i % 2 == 0) ? null : i);

        List<Integer> odds = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<Integer>(), ints(10));

        Integer[] expected = {1,3,5,7,9};

        assertTrue(odds.equals(Arrays.asList(expected)));
    }

    @Test
    public void testKeepIndexed() throws Exception {
        ITransducer<Integer, Integer> xf = keepIndexed(
                (Long idx, Integer integer) ->
                        (idx == 1L || idx == 4L) ? integer : null
        );

        List<Integer> nums = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), ints(10));

        Integer[] expected = {0,3};

        assertTrue(nums.equals(Arrays.asList(expected)));
    }

    @Test
    public void testDedupe() throws Exception {
        Integer[] seed = {1,2,2,3,4,5,5,5,5,5,5,5,0};
        ITransducer<Integer, Integer> xf = dedupe();

        List<Integer> nums = transduce(xf, (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<>(), Arrays.asList(seed));

        Integer[] expected = {1,2,3,4,5,0};

        assertTrue(nums.equals(Arrays.asList(expected)));
    }

    @Test
    public void testPartitionBy() throws Exception {
        Integer[] seed = {1,1,1,2,2,3,4,5,5};

        ITransducer<Iterable<Integer>, Integer> xf = partitionBy(Function.identity());

        List<List<Integer>> vals = transduce(xf, (result, input, reduced) -> {
            List<Integer> ret = new ArrayList<>();
            for (Integer i : input) {
                ret.add(i);
            }
            result.add(ret);
            return result;
        }, new ArrayList<>(), Arrays.asList(seed));

        final Integer[] a = {1,1,1};
        final Integer[] b = {2,2};
        final Integer[] c = {3};
        final Integer[] d = {4};
        final Integer[] e = {5,5};

        List<List<Integer>> expected = new ArrayList<List<Integer>>() {{
            add(Arrays.asList(a));
            add(Arrays.asList(b));
            add(Arrays.asList(c));
            add(Arrays.asList(d));
            add(Arrays.asList(e));
        }};

        assertTrue(vals.size() == 5);

        for(int i=0; i<expected.size(); i++) {
            assertEquals(vals.get(i).size(),expected.get(i).size());
            assertTrue(vals.get(i).equals(expected.get(i)));
        }
    }

    @Test
    public void testPartitionAll() throws Exception {
        ITransducer<Iterable<Integer>, Integer> xf = partitionAll(3);

        List<List<Integer>> vals = transduce(xf, (result, input, reduced) -> {
            List<Integer> ret = new ArrayList<>();
            for (Integer i : input) {
                ret.add(i);
            }
            result.add(ret);
            return result;
        }, new ArrayList<>(), ints(10));

        final Integer[] a = {0,1,2};
        final Integer[] b = {3,4,5};
        final Integer[] c = {6,7,8};
        final Integer[] d = {9};

        List<List<Integer>> expected = new ArrayList<List<Integer>>() {{
            add(Arrays.asList(a));
            add(Arrays.asList(b));
            add(Arrays.asList(c));
            add(Arrays.asList(d));
        }};

        assertTrue(vals.size() == 4);

        for(int i=0; i<expected.size(); i++) {
            assertEquals(vals.get(i).size(),expected.get(i).size());
            assertTrue(vals.get(i).equals(expected.get(i)));
        }
    }

    @Test
    public void testSimpleCovariance() throws Exception {
        ITransducer<Integer, Integer> m = map(i -> i * 2);

        List<Integer> inputs = new ArrayList<Integer>() {{
            for(int i : ints(20)) {
                add(i);
            }
        }};

        Collection<Number> res = transduce(m, (result, input, reduced) -> {
                result.add(input);
                return result;
        }, new ArrayList<>(inputs.size()), inputs);

        assertEquals(20, res.size());

        ITransducer<Number, Number> f = filter(number -> number.doubleValue() > 10.0);

        res = transduce(m.comp(f), (result, input, reduced) -> {
            result.add(input);
            return result;
        }, new ArrayList<Number>(inputs.size()), inputs);

        assertEquals(14, res.size());
    }
}