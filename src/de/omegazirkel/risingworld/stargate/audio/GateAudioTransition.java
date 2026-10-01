package de.omegazirkel.risingworld.stargate.audio;

/** Cues emitted only for a new authoritative gate phase or dial step. */
record GateAudioTransition(boolean changedMode, String stateCue, boolean startOutgoingStep) {
    static GateAudioTransition between(String oldState, String oldDirection, long oldStep,
            String nextState, String nextDirection, long nextStep, int nextChevrons) {
        boolean changed = !nextState.equals(oldState) || !nextDirection.equals(oldDirection);
        String cue = null;
        if (changed) {
            if ("OPEN".equals(nextState)) cue = "gate_open.ogg";
            else if ("IDLE".equals(nextState)) {
                if ("OPEN".equals(oldState)) cue = "shutdown_b.ogg";
                else if ("OUTGOING".equals(oldState)) cue = "dial_fail.ogg";
            }
        }
        boolean step = "OUTGOING".equals(nextState) && nextStep != 0
                && nextStep != oldStep && nextChevrons < 7;
        return new GateAudioTransition(changed, cue, step);
    }
}
