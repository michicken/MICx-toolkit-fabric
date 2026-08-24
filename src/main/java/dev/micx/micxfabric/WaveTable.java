package dev.micx.micxfabric;

public final class WaveTable {
    public enum ZbMap { DEAD_END(30), BAD_BLOOD(30), PRISON(30), THE_LAB(40);
        private final int maxRound; ZbMap(int v){maxRound=v;} public int maxRound(){return maxRound;}
    }
    private static final int[] EMPTY = new int[0];
    private static final int[][] DE = {
        {10, 20}, {10, 20}, {10, 20, 35}, {10, 20, 35}, {10, 22, 37}, {10, 22, 44}, {10, 25, 47}, {10, 25, 50},
        {10, 22, 38}, {10, 24, 45}, {10, 25, 48}, {10, 25, 50}, {10, 25, 50}, {10, 25, 45}, {10, 25, 46}, {10, 24, 47},
        {10, 24, 47}, {10, 24, 47}, {10, 24, 47}, {10, 24, 49}, {10, 23, 44}, {10, 23, 45}, {10, 23, 42}, {10, 23, 43},
        {10, 23, 43}, {10, 23, 36}, {10, 24, 44}, {10, 24, 42}, {10, 24, 42}, {10, 24, 45}
    };
    private static final int[][] BB = {
        {10, 22}, {10, 22}, {10, 22}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34},
        {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34},
        {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34},
        {10, 22, 34}, {10, 24, 38}, {10, 24, 38}, {10, 22, 34}, {10, 24, 38}, {10, 22, 34}
    };
    private static final int[][] PR = {
        {10, 20}, {10, 20, 30}, {10, 17, 24, 31}, {10, 17, 24, 31}, {10, 20, 30}, {10, 20, 30}, {10, 20, 30}, {10, 25, 40},
        {10, 25, 35}, {10, 25, 45}, {10, 25, 40}, {10, 25, 37}, {10, 22, 34}, {10, 25, 37}, {10, 25, 40}, {10, 22, 37},
        {10, 22, 42}, {10, 25, 45}, {10, 25, 45}, {10, 25, 40}, {10, 20, 35, 55, 75}, {10, 25, 40}, {10, 30, 50}, {10, 30, 50},
        {10, 25, 45}, {10, 30, 50}, {10, 25, 45}, {10, 30, 50}, {10, 30, 55}, {10}
    };
    private static final int[][] LAB = {
        {10, 22}, {10, 22}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34},
        {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34},
        {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34},
        {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34},
        {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}, {10, 22, 34}
    };
    private WaveTable(){}
    private static int[][] table(ZbMap m){ return switch(m){ case DEAD_END->DE; case BAD_BLOOD->BB; case PRISON->PR; case THE_LAB->LAB; }; }
    public static int[] waveTimes(ZbMap map, int round){ if(map==null||round<1||round>map.maxRound()) return EMPTY; return table(map)[round-1]; }
    public static int waveAt(int[] times, long elapsedMs){ int w=0; for(int i=0;i<times.length;i++) if(elapsedMs>=times[i]*1000L) w=i+1; return w; }
    public static ZbMap detect(String s){ if(s==null) return null; String L=s.toLowerCase(); if(L.contains("dead end")) return ZbMap.DEAD_END; if(L.contains("bad blood")) return ZbMap.BAD_BLOOD; if(L.contains("prison")) return ZbMap.PRISON; if(L.contains("the lab")) return ZbMap.THE_LAB; return null; }
    public static int waveColor(boolean aa,int round,int wave,int nextWave){
        if(nextWave==0||wave<nextWave) return 0x5A5A5A;
        if(wave==nextWave){ if(aa){ if(contains(ZombiesRoundData.giantWaves(round),wave)) return 0x0099FF; if(contains(ZombiesRoundData.tooWaves(round),wave)) return 0x00FF00; if(contains(ZombiesRoundData.tooGiantWaves(round),wave)) return 0xFF0000; } return 0xFFFF00; }
        if(aa){ if(contains(ZombiesRoundData.giantWaves(round),wave)) return 0x663399; if(contains(ZombiesRoundData.tooWaves(round),wave)) return 0x006666; if(contains(ZombiesRoundData.tooGiantWaves(round),wave)) return 0x783300; }
        return 0x808080;
    }
    private static boolean contains(int[] a,int v){ for(int x:a) if(x==v) return true; return false; }
    public static String formatWaveTime(int s){ if(s<=0) return "00:00"; int m=s/60,r=s%60; return (m<10?"0"+m:m)+":"+(r<10?"0"+r:r); }
}
