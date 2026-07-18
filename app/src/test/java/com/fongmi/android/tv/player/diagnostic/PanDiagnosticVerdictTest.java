package com.fongmi.android.tv.player.diagnostic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PanDiagnosticVerdictTest {

    @Test
    public void attributesSlowProviderWhenBaselineIsFast() {
        PanDiagnosticVerdict.Result result = resolve(80, 200, 40, 39, 38, 4, 0);
        assertEquals(PanDiagnosticVerdict.Cause.UPSTREAM_PROVIDER, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.HIGH, result.confidence());
    }

    @Test
    public void attributesExternalProxyLoss() {
        PanDiagnosticVerdict.Result result = resolve(80, 200, 160, 60, 58, 0, 0);
        assertEquals(PanDiagnosticVerdict.Cause.EXTERNAL_PROXY, result.cause());
    }

    @Test
    public void attributesAppDataSourceLoss() {
        PanDiagnosticVerdict.Result result = resolve(80, 200, 160, 150, 60, 0, 0);
        assertEquals(PanDiagnosticVerdict.Cause.APP_DATA_SOURCE, result.cause());
    }

    @Test
    public void largeEfficiencyLossIsNotRootCauseWhenCapacityRemainsSufficient() {
        PanDiagnosticVerdict.Result result = resolve(6, 200, 160, 68, 65, 0, 0);
        assertEquals(PanDiagnosticVerdict.Cause.SUFFICIENT, result.cause());
    }

    @Test
    public void refusesFalseCertaintyWhenEvidenceIsMissing() {
        PanDiagnosticVerdict.Result result = resolve(80, 0, 0, 0, 0, 3, 0);
        assertEquals(PanDiagnosticVerdict.Cause.INCONCLUSIVE, result.cause());
        assertEquals(PanDiagnosticVerdict.Confidence.LOW, result.confidence());
    }

    private static PanDiagnosticVerdict.Result resolve(long requiredMbps, long baselineMbps, long upstreamMbps,
                                                       long proxyMbps, long dataSourceMbps, int rebuffer, int dropped) {
        return PanDiagnosticVerdict.resolve(new PanDiagnosticVerdict.Input(mbps(requiredMbps), mbps(baselineMbps), mbps(upstreamMbps), mbps(proxyMbps), mbps(dataSourceMbps), rebuffer, dropped));
    }

    private static long mbps(long value) {
        return value * 1_000_000L;
    }
}
