//For constructing tunnels and cuttings

importPackage(Packages.java.math);
importPackage(Packages.java.lang);
importPackage(Packages.com.sk89q.worldedit);
importPackage(Packages.com.sk89q.worldedit.math);
importPackage(Packages.com.sk89q.worldedit.session);
importPackage(Packages.com.sk89q.worldedit.regions);
importPackage(Packages.com.sk89q.worldedit.world.block);
importPackage(Packages.com.sk89q.worldedit.extension.input);
importPackage(Packages.com.sk89q.worldedit.extent.world);
importPackage(Packages.com.sk89q.worldedit.function.mask);

//argv:
//1: material of track bed
//2: material of the tunnel and cutting wall and tunnel ceiling
//3: tunnel height
const tunnelHeight = new Integer(argv[3]);
//4: height above tunnel height where trees should be removed
const removeTreeHeight = argv[4];

const editSession = context.remember();
const localSession = context.getSession();
// The player's region selection
const selectedRegion = localSession.getSelection();
const parserContext = new ParserContext();

//Initialise parserContext
parserContext.setExtent(new SideEffectExtent(editSession.getWorld()));
parserContext.setSession(localSession);
parserContext.setWorld(editSession.getWorld());
parserContext.setActor(player);

//Define masks
const trackMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[1], parserContext);
const replaceableBlockMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("##transit_corridor:railway_embankment_replaceable", parserContext);
const airMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("minecraft:air", parserContext);
const railMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("mtr:rail", parserContext);
const groundMask = new MaskIntersection(new MaskIntersection(Masks.negate(replaceableBlockMask), Masks.negate(trackMask)), Masks.negate(railMask));

editSession.setMask(Masks.negate(railMask));

//Provide feedback to user
let blocksChanged = 0;
let blocksEvaluated = 0;
let isOnEdgeCount = 0;
let isTunnelCount = 0;
let isTransitionCount = 0;
const regionSize = selectedRegion.getVolume();
context.print("Creating corridor...");

//Loop through all blocks in selection
const iterator = selectedRegion.iterator();

while (iterator.hasNext()) {
    let point = iterator.next();

    if (trackMask.test(point)) {
        const neighbouringTrackBlocksCount = Number(trackMask.test(point.add(1,0,0)) || trackMask.test(point.add(1,1,0)) || trackMask.test(point.add(1,-1,0)))+
            Number(trackMask.test(point.add(0,0,1)) || trackMask.test(point.add(0,1,1)) || trackMask.test(point.add(0,-1,1)))+
            Number(trackMask.test(point.add(-1,0,0)) || trackMask.test(point.add(-1,1,0)) || trackMask.test(point.add(-1,-1,0)))+
            Number(trackMask.test(point.add(0,0,-1)) || trackMask.test(point.add(0,1,-1)) || trackMask.test(point.add(0,-1,-1)))+
            Number(trackMask.test(point.add(1,0,1)) || trackMask.test(point.add(1,1,1)) || trackMask.test(point.add(1,-1,1)))+
            Number(trackMask.test(point.add(1,0,-1)) || trackMask.test(point.add(1,1,-1)) || trackMask.test(point.add(1,-1,-1)))+
            Number(trackMask.test(point.add(-1,0,-1)) || trackMask.test(point.add(-1,1,-1)) || trackMask.test(point.add(-1,-1,-1)))+
            Number(trackMask.test(point.add(-1,0,1)) || trackMask.test(point.add(-1,1,1)) || trackMask.test(point.add(-1,-1,1)));

        const other = [Number(trackMask.test(point.add(1,0,0)) || trackMask.test(point.add(1,1,0)) || trackMask.test(point.add(1,-1,0))),
            Number(trackMask.test(point.add(0,0,1)) || trackMask.test(point.add(0,1,1)) || trackMask.test(point.add(0,-1,1))),
            Number(trackMask.test(point.add(-1,0,0)) || trackMask.test(point.add(-1,1,0)) || trackMask.test(point.add(-1,-1,0))),
            Number(trackMask.test(point.add(0,0,-1)) || trackMask.test(point.add(0,1,-1)) || trackMask.test(point.add(0,-1,-1))),
            Number(trackMask.test(point.add(1,0,1)) || trackMask.test(point.add(1,1,1)) || trackMask.test(point.add(1,-1,1))),
            Number(trackMask.test(point.add(1,0,-1)) || trackMask.test(point.add(1,1,-1)) || trackMask.test(point.add(1,-1,-1))),
            Number(trackMask.test(point.add(-1,0,-1)) || trackMask.test(point.add(-1,1,-1)) || trackMask.test(point.add(-1,-1,-1))),
            Number(trackMask.test(point.add(-1,0,1)) || trackMask.test(point.add(-1,1,1)) || trackMask.test(point.add(-1,-1,1)))];
        context.print(other);
        const isOnEdge = neighbouringTrackBlocksCount < 8;
        const isBelowTrack = trackMask.test(point.add(0,1,0));
        const ceilingLocation = point.add(0, tunnelHeight, 0);
        const isTunnel = groundMask.test(ceilingLocation.add(BlockVector3.UNIT_Y));
        const isTransition = !isTunnel &&
        (groundMask.test(ceilingLocation.add(1,1,0)) ||
        groundMask.test(ceilingLocation.add(-1,1,0)) ||
        groundMask.test(ceilingLocation.add(0,1,1)) ||
        groundMask.test(ceilingLocation.add(0,1,-1)) ||
        groundMask.test(ceilingLocation.add(-1,1,-1)) ||
        groundMask.test(ceilingLocation.add(1,1,-1)) ||
        groundMask.test(ceilingLocation.add(1,1,1)) ||
        groundMask.test(ceilingLocation.add(-1,1,1)));

        if (isOnEdge) {
            isOnEdgeCount++;
        }
        if (isTunnel) {
            isTunnelCount++;
        }
        if (isTransition) {
            isTransitionCount++;
        }

        if (isTunnel) {
            if (isOnEdge) {
                //Tunnel wall
                for (i = 0; i < tunnelHeight; i++) {
                    let wallBlock = point.add(0,i+1,0);
                    editSession.setBlock(wallBlock, BlockTypes.get(argv[2]).getDefaultState());
                    blocksChanged++;
                }
            }
            //Tunnel ceiling
            editSession.setBlock(ceilingLocation, BlockTypes.get(argv[2]).getDefaultState());
            blocksChanged++;
            if (!(trackMask.test(ceilingLocation.add(BlockVector3.UNIT_X)) && trackMask.test(ceilingLocation.add(BlockVector3.UNIT_Z)) && trackMask.test(ceilingLocation.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(ceilingLocation.add(BlockVector3.UNIT_MINUS_Z)))) {
                editSession.setBlock(ceilingLocation.subtract(0,1,0), BlockTypes.get(argv[2]).getDefaultState());
                blocksChanged++;
            }

            //Set air blocks between the tunnel ceiling and tunnel floor
            clearAbove(point, tunnelHeight);

        } else {
            if (isOnEdge) {
                //Retaining wall of cutting
                let i = 0;
                while (true) {
                    i++;
                    let wallBlock = point.add(0,i,0);

                    let wallBlockTouchingGround =
                        groundMask.test(wallBlock.add(1,0,0)) ||
                        groundMask.test(wallBlock.add(-1,0,0)) ||
                        groundMask.test(wallBlock.add(0,0,1)) ||
                        groundMask.test(wallBlock.add(0,0,-1)) ||
                        groundMask.test(wallBlock.add(-1,0,-1)) ||
                        groundMask.test(wallBlock.add(1,0,-1)) ||
                        groundMask.test(wallBlock.add(1,0,1)) ||
                        groundMask.test(wallBlock.add(-1,0,1));

                    if (wallBlockTouchingGround) {
                        editSession.setBlock(wallBlock, BlockTypes.get(argv[2]).getDefaultState());
                        blocksChanged++;
                    } else {
                        break;
                    }
                }
            } else {
                //Set air blocks between the tunnel ceiling and tunnel floor
                clearAbove(point, tunnelHeight);
            }
            if (isTransition) {
                //Tunnel-cutting transition
                editSession.setBlock(ceilingLocation, BlockTypes.get(argv[2]).getDefaultState());
                blocksChanged++;

                let i = 0;
                while (true) {
                    i++;
                    const wallBlock = ceilingLocation.add(0,i,0);

                    let wallBlockTouchingGround =
                        groundMask.test(wallBlock.add(1,0,0)) ||
                        groundMask.test(wallBlock.add(-1,0,0)) ||
                        groundMask.test(wallBlock.add(0,0,1)) ||
                        groundMask.test(wallBlock.add(0,0,-1)) ||
                        groundMask.test(wallBlock.add(-1,0,-1)) ||
                        groundMask.test(wallBlock.add(1,0,-1)) ||
                        groundMask.test(wallBlock.add(1,0,1)) ||
                        groundMask.test(wallBlock.add(-1,0,1));

                    if (replaceableBlockMask.test(wallBlock) && wallBlockTouchingGround) {
                        editSession.setBlock(wallBlock, BlockTypes.get(argv[2]).getDefaultState());
                        blocksChanged++;
                    } else {
                        // Clear space above wall
                        clearAbove(wallBlock, removeTreeHeight - i);
                        break;
                    }
                }
            }
        }
    }
    blocksEvaluated++;
    if (blocksEvaluated % 50000 == 0) {
        context.print(new String(Math.round(100 * blocksEvaluated / regionSize)).concat("% complete"));
    }
}

context.print("Corridor successfully created.");
context.print(new String(trackMask));
context.print(new String(blocksChanged).concat(" blocks were changed."));
context.print(new String(isOnEdgeCount).concat(" blocks were on edge"));
context.print(new String(isTunnelCount).concat(" blocks were tunnel floor"));
context.print(new String(isTransitionCount).concat(" block were transition floor"));

function clearAbove(point, height) {
    for (i = 1; i < height; i++) {
        if (!airMask.test(point.add(0, i, 0))) {
            editSession.setBlock(point.add(0, i, 0), BlockTypes.get("minecraft:air").getDefaultState());
            blocksChanged++;
        }
    }
}

