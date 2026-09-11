package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RoundTimeNotifier {
    public static final String MODE_ALL="ALL", MODE_QUINTUPLE="QUINTUPLE", MODE_TENFOLD="TENFOLD", MODE_OFF="OFF";
    private RoundTimeNotifier(){}
    public static final class Announcement{ public final int round; public final long durationMs; public final String copyPlain; Announcement(int r,long d,String c){round=r;durationMs=d;copyPlain=c;}}
    public static Announcement buildAnnouncement(String mode,int prevRound,long prevStart,long now){
        if(prevRound<1||prevStart<=0) return null; long d=now-prevStart; if(d<0) return null;
        if(MODE_ALL.equalsIgnoreCase(mode)){}
        else if(MODE_QUINTUPLE.equalsIgnoreCase(mode)){ if(prevRound%5!=0) return null; }
        else if(MODE_TENFOLD.equalsIgnoreCase(mode)){ if(prevRound%10!=0) return null; }
        else return null;
        return new Announcement(prevRound,d,"You completed Round "+prevRound+" in "+formatDuration(d)+"!");
    }
    public static String formatDuration(long ms){
        long s=(ms+500)/1000; long h=s/3600,m=s%3600/60,r=s%60;
        if(h>0) return String.format("%d:%02d:%02d",h,m,r);
        return String.format("%02d:%02d",m,r);
    }
    public static void sendAnnouncement(Announcement a){
        Minecraft mc=Minecraft.getInstance(); if(mc==null||mc.player==null) return;
        Component bar=Component.literal("§a§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        Component line=Component.literal("§e                     You completed §cRound "+a.round+" §ein §a"+formatDuration(a.durationMs)+"§e!");
        mc.player.sendSystemMessage(bar); mc.player.sendSystemMessage(line); mc.player.sendSystemMessage(bar);
    }
    public static void sendSplitAnnouncement(int round,long dR,long dT,boolean hasB,long dR2,long dT2){
        Minecraft mc=Minecraft.getInstance(); if(mc==null||mc.player==null) return;
        SpeedrunBaseline bl=SpeedrunBaseline.get();
        String la=bl.labelA(), lb=bl.labelB();
        mc.player.sendSystemMessage(splitLine("§7  R"+round+" §7["+la+"] ",dR,dT));
        if(hasB) mc.player.sendSystemMessage(splitLine("§7      §7["+lb+"] ",dR2,dT2));
    }
    private static Component splitLine(String prefix,long dR,long dT){
        String sR=SpeedrunBaseline.formatDelta(dR), sT=SpeedrunBaseline.formatDelta(dT);
        int cR=SpeedrunBaseline.deltaColor(dR), cT=SpeedrunBaseline.deltaColor(dT);
        String rLabel=dR<0?"领先":dR>0?"落后":"持平", tLabel=dT<0?"领先":dT>0?"落后":"持平";
        String colorR=cR==0xFF55FF55?"§a":cR==0xFFFF5555?"§c":"§7";
        String colorT=cT==0xFF55FF55?"§a":cT==0xFFFF5555?"§c":"§7";
        return Component.literal(prefix+"§e本回合"+rLabel+" "+colorR+sR+" §7| §e总计"+tLabel+" "+colorT+sT);
    }
}
