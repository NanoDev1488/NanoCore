package com.nanocore.adminhub.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class AdminHubPaperPlugin extends JavaPlugin implements Listener {

    private static final String API   = "http://localhost:8080/admin/api";
    private static final String[] SRV = {"velocity","paper-1.21.4","paper-1.21.11","bungee-legacy"};

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    private World world;
    private final Map<String,Boolean> status = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        world = makeWorld();
        buildPlatform();
        getCommand("nanocmd").setExecutor(new Cmd());
        getCommand("nanocmd").setTabCompleter(new CmdTab());
        getServer().getPluginManager().registerEvents(this, this);
        new BukkitRunnable(){
            @Override public void run(){ syncStatus(); }
        }.runTaskTimerAsynchronously(this, 20L, 200L);
        getLogger().info("AdminHub ready on world: " + world.getName());
    }

    private World makeWorld() {
        World w = Bukkit.getWorld("adminhub");
        if (w != null) return w;
        WorldCreator wc = new WorldCreator("adminhub")
            .type(WorldType.FLAT)
            .generatorSettings("{\"biome\":\"minecraft:the_void\",\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"structures\":{\"structures\":{}}}");
        w = wc.createWorld();
        Objects.requireNonNull(w).setSpawnLocation(20,65,15);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setTime(6000);
        return w;
    }

    private void buildPlatform() {
        for (int x=0; x<=44; x++) for (int z=0; z<=32; z++)
            world.getBlockAt(x,64,z).setType(
                (x==0||x==44||z==0||z==32)?Material.DARK_OAK_PLANKS:Material.BLACK_CONCRETE);

        sign(21,65,2,"§6§lNanoCore","§7Admin Hub","§8Нажми на плашку","");

        int[] xs={3,13,23,33};
        for (int i=0;i<SRV.length;i++) panel(xs[i],66,8,SRV[i]);

        sign(10,65,27,"§c§l■ STOP ALL","","","");  cmdBlock(10,63,27,"nanocmd stop all");
        sign(22,65,27,"§a§l▶ START ALL","","","");  cmdBlock(22,63,27,"nanocmd start all");
        sign(34,65,27,"§e§l↺ RESTART ALL","","","");cmdBlock(34,63,27,"nanocmd restart all");
    }

    private void panel(int x,int y,int z,String srv){
        for(int dx=-1;dx<=8;dx++) for(int dz=-1;dz<=9;dz++)
            if(dx==-1||dx==8||dz==-1||dz==9)
                world.getBlockAt(x+dx,y-2,z+dz).setType(Material.CYAN_TERRACOTTA);
        sign(x+2,y,z,"§b§l"+srv,"§7Управление:","","");
        sign(x,  y-1,z+3,"§a▶ START","","","");   cmdBlock(x,  y-2,z+3,"nanocmd start "+srv);
        sign(x+3,y-1,z+3,"§c■ STOP","","","");    cmdBlock(x+3,y-2,z+3,"nanocmd stop "+srv);
        sign(x+6,y-1,z+3,"§e↺ RESTART","","",""); cmdBlock(x+6,y-2,z+3,"nanocmd restart "+srv);
        sign(x+3,y-1,z+7,"§7Статус:","§e...","","");
    }

    private void syncStatus() {
        try {
            String json = http.send(
                HttpRequest.newBuilder(URI.create(API+"/status")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
            for (String s:SRV) status.put(s,json.contains("\""+s+"\":{\"running\":true"));
            Bukkit.getScheduler().runTask(this,this::refreshSigns);
        } catch(Exception ignored){}
    }

    private void refreshSigns(){
        int[] xs={3,13,23,33};
        for(int i=0;i<SRV.length;i++){
            boolean on=status.getOrDefault(SRV[i],false);
            sign(xs[i]+3,65,15,"§7Статус:",on?"§a● RUNNING":"§c● STOPPED","","");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        e.getPlayer().setGameMode(GameMode.ADVENTURE);
        e.getPlayer().teleport(world.getSpawnLocation().add(0.5,0,0.5));
        e.getPlayer().sendMessage(Component.text("NanoCore Admin Hub — /nanocmd"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        Bukkit.getScheduler().runTaskLater(this,()->{
            if(Bukkit.getOnlinePlayers().isEmpty())
                try{post(API+"/adminhub","{\"action\":\"idle\"}");}catch(Exception ignored){}
        },40L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        if(e.getClickedBlock()==null||!e.getAction().name().contains("RIGHT")) return;
        if(!e.getClickedBlock().getType().name().contains("SIGN")) return;
        Block below=e.getClickedBlock().getRelative(BlockFace.DOWN);
        if(below.getType()!=Material.COMMAND_BLOCK) return;
        if(below.getState() instanceof CommandBlock cb){
            String cmd=cb.getCommand();
            if(cmd!=null&&!cmd.isBlank()) e.getPlayer().performCommand(cmd);
        }
        e.setCancelled(true);
    }

    private void sign(int x,int y,int z,String l1,String l2,String l3,String l4){
        Block b=world.getBlockAt(x,y,z);
        b.setType(Material.OAK_SIGN,false);
        if(b.getState() instanceof Sign s){
            var side=s.getSide(Side.FRONT);
            side.line(0,Component.text(l1)); side.line(1,Component.text(l2));
            side.line(2,Component.text(l3)); side.line(3,Component.text(l4));
            s.update(true,false);
        }
    }

    private void cmdBlock(int x,int y,int z,String cmd){
        Block b=world.getBlockAt(x,y,z);
        b.setType(Material.COMMAND_BLOCK,false);
        if(b.getState() instanceof CommandBlock cb){
            cb.setCommand(cmd); cb.update(true,false);
        }
    }

    private void post(String url,String json) throws Exception{
        http.send(HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
            HttpResponse.BodyHandlers.discarding());
    }

    private class Cmd implements CommandExecutor{
        @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){
            if(!(s instanceof Player p)) return true;
            if(!p.hasPermission("nanocore.adminhub")){p.sendMessage("§cНет доступа.");return true;}
            if(a.length<1){p.sendMessage("§7/nanocmd <start|stop|restart|list> [server]");return true;}
            String act=a[0].toLowerCase(), tgt=a.length>1?a[1]:"all";
            if("list".equals(act)){status.forEach((srv,on)->p.sendMessage((on?"§a● ":"§c● ")+srv));return true;}
            try{post(API+"/cmd","{\"server\":\""+tgt+"\",\"action\":\""+act+"\"}");
                p.sendMessage(Component.text("§a✅ ")+act+" → "+tgt);}
            catch(Exception ex){p.sendMessage(Component.text("§c❌ ")+ex.getMessage());}
            return true;
        }
    }
    private class CmdTab implements TabCompleter{
        @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){
            if(args.length==1) return List.of("start","stop","restart","list");
            if(args.length==2) return List.of(SRV);
            return List.of();
        }
    }
}
