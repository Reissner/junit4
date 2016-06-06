package org.junit.runner;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * A <code>Result</code> collects and summarizes information from running multiple tests. 
 * A {@link RunListener} is notified on the result by invoking {@link RunListener#testRunFinished(Result)}. 
 * All tests are counted -- additional information is collected from tests that fail.
 *
 * @since 4.0
 */
public class Result implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final ObjectStreamField[] serialPersistentFields =
            ObjectStreamClass.lookup(SerializedForm.class).getFields();
            
    /**
     * The number of tests run so far, whether passed, failed or about to run. 
     * This does not include ignored tests but those with failed assumtions. 
     */
    private final AtomicInteger count;
    
    /**
     * The number of tests that failed during the run. 
     * This includes neither the ignored tests nor those with failed assumption. 
     */
    private final AtomicInteger ignoreCount;
    
    /**
     * The list of failures collected so far. 
     */
    private final CopyOnWriteArrayList<Failure> failures;
    
    /**
     * The number of milliseconds it took to run the entire suite to run, 
     * i.e. the time from invoking {@link RunListener#testRunStarted(Description)} 
     * to invoking {@link RunListener#testRunFinished(Description)}. 
     */
    private final AtomicLong runTime;
    
    /**
     * The time when starting the test suite as returned by {@link System#currentTimeMillis()}. 
    private final AtomicLong startTime;

    /** Only set during deserialization process. */
    private SerializedForm serializedForm;

    public Result() {
        count = new AtomicInteger();
        ignoreCount = new AtomicInteger();
        failures = new CopyOnWriteArrayList<Failure>();
        runTime = new AtomicLong();
        startTime = new AtomicLong();
    }

    private Result(SerializedForm serializedForm) {
        count = serializedForm.fCount;
        ignoreCount = serializedForm.fIgnoreCount;
        failures = new CopyOnWriteArrayList<Failure>(serializedForm.fFailures);
        runTime = new AtomicLong(serializedForm.fRunTime);
        startTime = new AtomicLong(serializedForm.fStartTime);
    }

    /**
     * Returns the number of tests run so far, whether passed, failed or about to run. 
     * This does not include ignored tests but those with failed assumtions. 
     * This is the number of invocations of {@link RunListener#testFinished(Description)} 
     * and hence of {@link RunListener#testStarted(Description)}. 
     *
     * @return the number of tests run
     */
    public int getRunCount() {
        return count.get();
    }

    /**
     * Returns the number of tests that failed during the run. 
     * This includes neither the ignored tests nor those with failed assumptions, 
     * although the latter are notified through {@link RunListener#testAssumptionFailure(Failure)}. 
     * This is the number of invocations of {@link RunListener#testFailed(Failure)}. 
     *
     * @return the number of tests that failed during the run
     */
    public int getFailureCount() {
        return failures.size();
    }

    /**
     * Returns the number of milliseconds it took to run the entire suite to run, 
     * i.e. the time from invoking {@link RunListener#testRunStarted(Description)} 
     * to invoking {@link RunListener#testRunFinished(Description)}. 
     * 
     * @return the number of milliseconds it took to run the entire suite to run
     */
    public long getRunTime() {
        return runTime.get();
    }

    /**
     * Returns the list of failures collected. 
     * Neither ignored tests nor tests with failed assumptions count as failures. 
     * Failures are added by invoking {@link RunListener#testFailure(Failure)}. 
     * 
     * @return the {@link Failure}s describing tests that failed and the problems they encountered
     */
    public List<Failure> getFailures() {
        return failures;
    }

    /**
     * Returns the number of tests ignored during the run. 
     * This does not include the tests with failed assumption. 
     * 
     * @return the number of tests ignored during the run
     */
    public int getIgnoreCount() {
        return ignoreCount.get();
    }

    /**
     * Returns whether the whole test succeeded, 
     * i.e. whether no failure occurred according to {@link #failures}. 
     * 
     * @return <code>true</code> if all tests succeeded
     */
    public boolean wasSuccessful() {
        return getFailureCount() == 0;
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        SerializedForm serializedForm = new SerializedForm(this);
        serializedForm.serialize(s);
    }

    private void readObject(ObjectInputStream s)
            throws ClassNotFoundException, IOException {
        serializedForm = SerializedForm.deserialize(s);
    }

    private Object readResolve()  {
        return new Result(serializedForm);
    }

    @RunListener.ThreadSafe
    private class Listener extends RunListener {
        @Override
        public void testRunStarted(Description description) throws Exception {
            startTime.set(System.currentTimeMillis());
        }

        @Override
        public void testRunFinished(Result result) throws Exception {
            long endTime = System.currentTimeMillis();
            runTime.addAndGet(endTime - startTime.get());
        }

        @Override
        public void testFinished(Description description) throws Exception {
            count.getAndIncrement();
        }

        @Override
        public void testFailure(Failure failure) throws Exception {
            failures.add(failure);
        }

        @Override
        public void testIgnored(Description description) throws Exception {
            ignoreCount.getAndIncrement();
        }

        @Override
        public void testAssumptionFailure(Failure failure) {
            // do nothing: same as passing (for 4.5; may change in 4.6)
        }
    }

    /**
     * Internal use only.
     */
    public RunListener createListener() {
        return new Listener();
    }

    /**
     * Represents the serialized output of {@code Result}. The fields on this
     * class match the files that {@code Result} had in JUnit 4.11.
     */
    private static class SerializedForm implements Serializable {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger fCount;
        private final AtomicInteger fIgnoreCount;
        private final List<Failure> fFailures;
        private final long fRunTime;
        private final long fStartTime;

        public SerializedForm(Result result) {
            fCount = result.count;
            fIgnoreCount = result.ignoreCount;
            fFailures = Collections.synchronizedList(new ArrayList<Failure>(result.failures));
            fRunTime = result.runTime.longValue();
            fStartTime = result.startTime.longValue();
        }

        @SuppressWarnings("unchecked")
        private SerializedForm(ObjectInputStream.GetField fields) throws IOException {
            fCount = (AtomicInteger) fields.get("fCount", null);
            fIgnoreCount = (AtomicInteger) fields.get("fIgnoreCount", null);
            fFailures = (List<Failure>) fields.get("fFailures", null);
            fRunTime = fields.get("fRunTime", 0L);
            fStartTime = fields.get("fStartTime", 0L);
        }

        public void serialize(ObjectOutputStream s) throws IOException {
            ObjectOutputStream.PutField fields = s.putFields();
            fields.put("fCount", fCount);
            fields.put("fIgnoreCount", fIgnoreCount);
            fields.put("fFailures", fFailures);
            fields.put("fRunTime", fRunTime);
            fields.put("fStartTime", fStartTime);
            s.writeFields();
        }

        public static SerializedForm deserialize(ObjectInputStream s)
                throws ClassNotFoundException, IOException {
            ObjectInputStream.GetField fields = s.readFields();
            return new SerializedForm(fields);
        }
    }
}
