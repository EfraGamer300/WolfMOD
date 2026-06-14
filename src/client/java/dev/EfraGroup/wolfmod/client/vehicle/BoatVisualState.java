package dev.EfraGroup.wolfmod.client.vehicle;

final class BoatVisualState {
    float currentPitch;
    float previousPitch;
    float currentRoll;
    float previousRoll;
    float currentAirYawInput;
    float previousAirYawInput;
    float currentAirPitch;
    float previousAirPitch;
    float currentAirRoll;
    float previousAirRoll;

    void beginTick() {
        previousPitch = currentPitch;
        previousRoll = currentRoll;
        previousAirYawInput = currentAirYawInput;
        previousAirPitch = currentAirPitch;
        previousAirRoll = currentAirRoll;
    }
}
