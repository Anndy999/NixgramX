#ifndef NIXGRAMX_GROUP_CALL_AUDIO_BITRATE_H
#define NIXGRAMX_GROUP_CALL_AUDIO_BITRATE_H

// NixgramX: custom group-call outgoing audio bitrate (bps), 0 = official
// behaviour. Set by the NixgramX GroupInstanceCustomImpl overlay, read by
// webrtc_voice_engine.cc. Kept outside the official tgcalls submodule.
namespace nixgramx {

int groupCallCustomAudioBitrateBps();
void setGroupCallCustomAudioBitrateBps(int bps);

} // namespace nixgramx

#endif
