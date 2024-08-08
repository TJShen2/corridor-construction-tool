package com.example;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.world.SideEffectExtent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class CorridorConstructionConstants {
  private final ServerPlayerEntity fabricPlayerEntity;
  private final Player actor;
  private final SessionManager manager;
  private final LocalSession localSession;
  private final World selectionWorld;
  private final Region selectedRegion;
  private final ParserContext parserContext = new ParserContext();

  public ServerPlayerEntity getFabricPlayerEntity() {
    return this.fabricPlayerEntity;
  }

  public Player getActor() {
    return this.actor;
  }

  public SessionManager getManager() {
    return this.manager;
  }

  public LocalSession getLocalSession() {
    return this.localSession;
  }

  public World getSelectionWorld() {
    return this.selectionWorld;
  }

  public Region getSelectedRegion() {
    return this.selectedRegion;
  }

  public ParserContext getParserContext() {
    return this.parserContext;
  }

  public CorridorConstructionConstants(ServerCommandSource source) {
    fabricPlayerEntity = source.getPlayer();
    actor = FabricAdapter.adaptPlayer(fabricPlayerEntity);
    manager = WorldEdit.getInstance().getSessionManager();
    localSession = manager.get(actor);
    selectionWorld = localSession.getSelectionWorld();
    selectedRegion = getSelection(actor, localSession, selectionWorld);

    //Initialise parserContext
    parserContext.setExtent(new SideEffectExtent(selectionWorld));
    parserContext.setSession(localSession);
    parserContext.setWorld(selectionWorld);
    parserContext.setActor(actor);
  }

  public Region getSelection(Player actor, LocalSession localSession, World selectionWorld) {
    try {
      if (selectionWorld == null) throw new IncompleteRegionException();
      return localSession.getSelection(selectionWorld);
    } catch (IncompleteRegionException e) {
      actor.printError(TextComponent.of("Please make a region selection first."));
      return new CuboidRegion(selectionWorld, BlockVector3.ZERO, BlockVector3.ZERO);
    }
  }
}
