package com.horrorcraft.lightsout;

import com.horrorcraft.lightsout.Roster.Actor;
import com.horrorcraft.lightsout.Roster.Role;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameListener implements Listener {

    private final LightsOutPlugin plugin;
    private final Game game;

    public GameListener(LightsOutPlugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    // ---------- three-hit rule ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Actor va = game.actor(victim);
        if (va == null) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return; // void stays lethal
        e.setCancelled(true); // campers, killer and police never take real damage

        if (!(e instanceof EntityDamageByEntityEvent be)) return;
        Player attacker = attacker(be.getDamager());
        Actor aa = game.actor(attacker);
        if (aa == null) return;

        if (va.role() == Role.KILLER && aa.hasLives()) {
            game.onKillerStruck(attacker, aa, victim);
        } else if (va.hasLives() && aa.role() == Role.KILLER && be.getDamager() == attacker) {
            game.onKillerSwing(attacker, victim, va);
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRegain(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p) {
            Actor a = game.actor(p);
            if (a != null && a.hasLives()) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && game.noSprint(p) && e.getFoodLevel() > 6) {
            e.setFoodLevel(6);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (e.isSprinting() && game.noSprint(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        EntityDamageEvent last = p.getLastDamageCause();
        game.onDeath(p, last == null ? "unknown" : last.getCause().name().toLowerCase());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> game.applyTier(p));
    }

    // ---------- stun: the killer cannot move while stunned ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition() || !game.killerStunned()) return;
        if (!game.isRole(e.getPlayer(), Role.KILLER)) return;
        Location to = e.getFrom().clone();
        to.setYaw(e.getTo().getYaw());
        to.setPitch(e.getTo().getPitch());
        e.setTo(to);
    }

    // ---------- join / quit ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (game.isRole(p, Role.KILLER)) {
            for (Player viewer : Bukkit.getOnlinePlayers()) game.applyKillerVisibility(viewer);
        } else {
            game.applyKillerVisibility(p);
        }
        Bukkit.getScheduler().runTask(plugin, () -> game.applyTier(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        game.dropInvolving(e.getPlayer().getUniqueId(), "quit");
        game.forget(e.getPlayer());
    }

    // ---------- carry ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player rider)) return;
        if (!game.carries().containsKey(rider.getUniqueId())) return;
        // Sneaking off or a teleport: put the rider back next tick. /lo drop removes the carry first.
        Bukkit.getScheduler().runTask(plugin, () -> game.remount(rider.getUniqueId()));
    }

    // ---------- proximity chat ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player speaker = e.getPlayer();
        Proximity.Pos from = game.positions().get(speaker.getUniqueId());
        String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
        Proximity.Line line = Proximity.parse(raw);
        if (!line.voiced()) e.message(Component.text(line.text()));

        List<String> heardBy = new ArrayList<>();
        e.viewers().removeIf(aud -> {
            if (!(aud instanceof Player listener)) return false; // console keeps everything
            if (listener.equals(speaker)) return false;
            boolean hears = Proximity.hears(from, game.positions().get(listener.getUniqueId()), game.chatRadius());
            if (hears) heardBy.add(game.idOf(listener));
            return !hears;
        });

        Actor a = game.actor(speaker);
        if (a != null && a.role() == Role.HUMAN) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("speaker", a.id());
            f.put("text", line.text());
            f.put("voiced", line.voiced());
            if (from != null) {
                f.put("x", Math.round(from.x() * 100) / 100.0);
                f.put("y", Math.round(from.y() * 100) / 100.0);
                f.put("z", Math.round(from.z() * 100) / 100.0);
            }
            f.put("heard_by", heardBy);
            game.outbox().emit("chat", f);
        }
    }
}
