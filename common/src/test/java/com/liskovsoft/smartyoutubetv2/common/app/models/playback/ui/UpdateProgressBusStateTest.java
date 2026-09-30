package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Plain JUnit on purpose, even though two paths here reach {@code android.util.Log}.
 *
 * <p>Robolectric was tried first and cannot run in this project at all: it needs its
 * {@code android-all} jar fetched at run time, and it is not in any local cache here. The
 * project's one pre-existing Robolectric test fails the same way - 10 of 10 with
 * {@code NoClassDefFoundError: android/webkit/RoboCookieManager} - so that is the state of
 * this machine, not something this test introduced.
 *
 * <p>What makes plain JUnit work is {@code testOptions.unitTests.returnDefaultValues = true}
 * in {@code common/build.gradle}: without it, {@code android.util.Log.e} throws
 * "not mocked" and any code that logs is untestable. With it, logging is a no-op on the JVM,
 * which is the right behaviour for a unit test.
 */

/**
 * GRTubeYou: the state the update panel holds between the download and the install button.
 *
 * <p>This class exists because the install button was unreachable three separate times, each
 * for a different reason. The bus exists to make the third one impossible: the "apk is on
 * disk" fact is kept here rather than only being pushed, so the order in which the download
 * callback and the row arrive stops mattering.
 *
 * <ul>
 *   <li>the fact was pushed to whoever happened to be listening, and the one row that cared
 *       was not attached yet - it is now replayed on registration;
 *   <li>the dialog is rebuilt between states and its teardown called {@code clear()}, so a
 *       returning screen found nothing - so {@code clear()} deliberately keeps the fact;
 *   <li>the row was laid out {@code focusable="false"}, so a visible "Install" still could
 *       not be reached with a remote. That one was a layout fault and is fixed in
 *       {@code onBindViewHolder}, not here.
 * </ul>
 *
 * <p>There is no public getter for the ready flag, and that is on purpose: it is written down
 * here as behaviour instead, by registering a target and watching whether it is told. A test
 * that reads a field through a getter would keep passing even if the replay stopped working.
 */
public class UpdateProgressBusStateTest {

    /** Records what the bus told it, standing in for the preference row. */
    private static class RecordingTarget implements UpdateProgressBus.Target {
        final List<String> events = new ArrayList<>();
        CharSequence installText;

        @Override
        public void onUpdateProgress(int percent, CharSequence status) {
            events.add("progress:" + percent);
        }

        @Override
        public void onUpdateReady(CharSequence text) {
            installText = text;
            events.add("ready");
        }
    }

    @Before
    public void setUp() {
        // The bus is static, so state would otherwise leak from one test into the next.
        UpdateProgressBus.reset();
        UpdateProgressBus.clear();
    }

    // --- the replay that makes the button reachable ----------------------------------

    @Test
    public void aRowRegisteredBeforeTheDownloadIsToldWhenItFinishes() {
        RecordingTarget row = new RecordingTarget();
        UpdateProgressBus.setTarget(row);

        UpdateProgressBus.pushReady("Install");

        assertEquals(1, row.events.size());
        assertEquals("ready", row.events.get(0));
    }

    @Test
    public void aRowRegisteredAfterTheDownloadIsStillTold() {
        // The order that used to leave the panel on a full bar with nothing to press.
        UpdateProgressBus.pushReady("Install");

        RecordingTarget late = new RecordingTarget();
        UpdateProgressBus.setTarget(late);

        assertEquals("the finished fact has to survive until a row asks for it", 1, late.events.size());
        assertEquals("ready", late.events.get(0));
    }

    @Test
    public void theReplayedTextIsTheOneThatWasPushed() {
        UpdateProgressBus.pushReady("Установить");

        RecordingTarget late = new RecordingTarget();
        UpdateProgressBus.setTarget(late);

        assertEquals("Установить", String.valueOf(late.installText));
    }

    @Test
    public void aRowThatIsNotThereCostsNothing() {
        // The download finishes while the panel is closed. That is the case where dropping
        // the signal is correct - nobody is looking - but it must not throw.
        UpdateProgressBus.pushReady("Install");
    }

    // --- teardown versus a new download -----------------------------------------------

    @Test
    public void theFinishedFactSurvivesThePanelBeingTornDown() {
        // The second unreachability bug. The dialog is rebuilt between states, and the old
        // row's onFinish called clear(); the rebuilt row then found nothing to show.
        UpdateProgressBus.pushReady("Install");

        UpdateProgressBus.clear();

        RecordingTarget afterTeardown = new RecordingTarget();
        UpdateProgressBus.setTarget(afterTeardown);

        assertEquals("the apk is still on disk, so the button is still real", 1, afterTeardown.events.size());
    }

    @Test
    public void aNewDownloadInvalidatesTheOldFinishedFact() {
        UpdateProgressBus.pushReady("Install");

        UpdateProgressBus.reset();

        RecordingTarget next = new RecordingTarget();
        UpdateProgressBus.setTarget(next);

        assertEquals("a row appearing mid-download must not be told the previous one finished",
                0, next.events.size());
    }

    @Test
    public void teardownStopsProgressGoingToTheOldRow() {
        RecordingTarget old = new RecordingTarget();
        UpdateProgressBus.setTarget(old);

        UpdateProgressBus.clear();
        UpdateProgressBus.push(50, "halfway");

        assertEquals(0, old.events.size());
    }

    // --- progress reaches a live row ---------------------------------------------------

    @Test
    public void progressGoesToALiveRow() {
        RecordingTarget row = new RecordingTarget();
        UpdateProgressBus.setTarget(row);

        UpdateProgressBus.push(50, "halfway");

        assertEquals(1, row.events.size());
        assertEquals("progress:50", row.events.get(0));
    }

    @Test
    public void progressIsSimplyDroppedWhenNothingIsListening() {
        // Best effort by design: the panel being closed mid-download is not an error.
        UpdateProgressBus.push(50, "halfway");
    }

    // --- the install action -------------------------------------------------------------

    @Test
    public void theInstallActionRunsWhenItIsRegistered() {
        AtomicInteger runs = new AtomicInteger();
        UpdateProgressBus.setInstallAction(runs::incrementAndGet);

        UpdateProgressBus.runInstallAction();

        assertEquals(1, runs.get());
    }

    @Test
    public void aPressWithNoActionRegisteredDoesNotThrow() {
        // It logs an error instead. Silently doing nothing was the bug rule 10 is about: a
        // button that looks live and is not is worse than a visible failure. The assertion is
        // that a stray press cannot take the dialog down with it.
        UpdateProgressBus.runInstallAction();
    }

    @Test
    public void theActionIsReplacedRatherThanAccumulated() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        UpdateProgressBus.setInstallAction(first::incrementAndGet);
        UpdateProgressBus.setInstallAction(second::incrementAndGet);

        UpdateProgressBus.runInstallAction();

        assertEquals("a second dialog must not be driven by the first one's closure", 0, first.get());
        assertEquals(1, second.get());
    }

    @Test
    public void teardownRemovesTheAction() {
        AtomicInteger runs = new AtomicInteger();
        UpdateProgressBus.setInstallAction(runs::incrementAndGet);

        UpdateProgressBus.clear();
        UpdateProgressBus.runInstallAction();

        assertEquals("a cleared action must not still be reachable", 0, runs.get());
    }

    @Test
    public void aNewDownloadKeepsTheActionButDropsTheReadyFlag() {
        // reset() is about the transfer, not about the installer's lifetime, so the action
        // registered by the presenter has to survive it.
        AtomicInteger runs = new AtomicInteger();
        UpdateProgressBus.setInstallAction(runs::incrementAndGet);

        UpdateProgressBus.reset();
        UpdateProgressBus.runInstallAction();

        assertEquals(1, runs.get());
    }

    // --- a fresh bus tells a row nothing -----------------------------------------------

    @Test
    public void aFreshBusTellsARowNothing() {
        RecordingTarget row = new RecordingTarget();
        UpdateProgressBus.setTarget(row);

        assertEquals(0, row.events.size());
    }

    @Test
    public void registeringTheSameRowTwiceDoesNotDuplicateTheReadySignal() {
        UpdateProgressBus.pushReady("Install");

        RecordingTarget row = new RecordingTarget();
        UpdateProgressBus.setTarget(row);
        UpdateProgressBus.setTarget(row);

        assertEquals("re-binding the same row must not stack a second Install", 1, row.events.size());
    }
}
