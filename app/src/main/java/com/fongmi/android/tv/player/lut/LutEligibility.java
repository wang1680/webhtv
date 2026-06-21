package com.fongmi.android.tv.player.lut;

import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.Locale;

public class LutEligibility {

    public static String getUnavailableReason(PlayerEngine engine, PlaySpec spec) {
        if (engine == null || !engine.supportsVideoEffects()) return ResUtil.getString(R.string.lut_unavailable_player);
        if (spec != null && spec.getDrm() != null) return ResUtil.getString(R.string.lut_unavailable_drm);
        if (PlayerSetting.isTunnel()) return ResUtil.getString(R.string.lut_unavailable_tunnel);
        if (engine.getDecode() == PlayerEngine.SOFT) return ResUtil.getString(R.string.lut_unavailable_soft_decode);
        if (PlayerSetting.isVideoPrefer()) return ResUtil.getString(R.string.lut_unavailable_video_prefer);
        if (isHdr(engine.getVideoFormat())) return ResUtil.getString(R.string.lut_unavailable_hdr);
        if (isKnownAudioOnly(engine.getCurrentTracks())) return ResUtil.getString(R.string.lut_unavailable_no_video);
        return null;
    }

    public static boolean isAvailable(PlayerEngine engine, PlaySpec spec) {
        return TextUtils.isEmpty(getUnavailableReason(engine, spec));
    }

    private static boolean isHdr(Format format) {
        if (format == null) return false;
        if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) return true;
        String codecs = format.codecs == null ? "" : format.codecs.toLowerCase(Locale.ROOT);
        if (codecs.contains("dvhe") || codecs.contains("dvh1")) return true;
        if (format.colorInfo == null) return false;
        int transfer = format.colorInfo.colorTransfer;
        return transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG;
    }

    private static boolean isKnownAudioOnly(Tracks tracks) {
        if (tracks == null || tracks.isEmpty()) return false;
        for (Tracks.Group group : tracks.getGroups()) if (group.getType() == C.TRACK_TYPE_VIDEO) return false;
        return true;
    }
}
