package com.tj;

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

/**
 * This record contains useful data that can be derived from a ServerCommandSource.
 * @author TJ Shen
 * @version 1.0.0
 */
public record CorridorConstructionConstants(ServerPlayerEntity fabricPlayerEntity, Player actor, SessionManager manager, LocalSession localSession, World selectionWorld, Region selectedRegion, ParserContext parserContext) {
  /**
   * Create a new instance from a ServerCommandSource.
   * @param source the ServerCommandSource instance to use for creating this instance
   * @return
   */
  public static CorridorConstructionConstants of(ServerCommandSource source) {
    ServerPlayerEntity fabricPlayerEntity = source.getPlayer();
    Player actor = FabricAdapter.adaptPlayer(fabricPlayerEntity);
    SessionManager manager = WorldEdit.getInstance().getSessionManager();
    LocalSession localSession = manager.get(actor);
    World selectionWorld = localSession.getSelectionWorld();
    Region selectedRegion = getSelection(actor, localSession, selectionWorld);

    //Initialise parserContext
    ParserContext parserContext = new ParserContext();
    parserContext.setExtent(new SideEffectExtent(selectionWorld));
    parserContext.setSession(localSession);
    parserContext.setWorld(selectionWorld);
    parserContext.setActor(actor);

    return new CorridorConstructionConstants(fabricPlayerEntity, actor, manager, localSession, selectionWorld, selectedRegion, parserContext);
  }

  private static Region getSelection(Player actor, LocalSession localSession, World selectionWorld) {
    try {
      if (selectionWorld == null) throw new IncompleteRegionException();
      return localSession.getSelection(selectionWorld);
    } catch (IncompleteRegionException e) {
      actor.printError(TextComponent.of("Please make a region selection first."));
      return new CuboidRegion(selectionWorld, BlockVector3.ZERO, BlockVector3.ZERO);
    }
  }
}
