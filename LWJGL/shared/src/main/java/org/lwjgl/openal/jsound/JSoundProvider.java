package org.lwjgl.openal.jsound;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.spi.MixerProvider;

/**
 * MixerProvider entry point, routing javax.sound.sampled AudioSystem / Clip /
 * SourceDataLine / TargetDataLine to the OpenAL backend, fixing the Android JRE
 * missing libjsound.so which broke mod music playback.
 *
 * <p>This jar belongs to the game library list; classes and the services declaration (META-INF/services/
 * javax.sound.sampled.spi.MixerProvider) sit together, so the JVM ServiceLoader can
 * discover and load them directly, unaffected by game class-loader isolation (e.g. Fabric knot).
 */
public class JSoundProvider extends MixerProvider {

    @Override
    public Mixer.Info[] getMixerInfo() {
        return new Mixer.Info[]{JSoundMixer.INFO};
    }

    @Override
    public boolean isMixerSupported(Mixer.Info info) {
        return JSoundMixer.INFO.equals(info);
    }

    @Override
    public Mixer getMixer(Mixer.Info info) {
        if (info == null || JSoundMixer.INFO.equals(info)) {
            return new JSoundMixer();
        }
        throw new IllegalArgumentException("Mixer not supported: " + info);
    }
}