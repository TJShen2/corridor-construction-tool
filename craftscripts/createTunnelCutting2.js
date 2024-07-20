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
//3: blocks that may be replaced
//4: tunnel height
var tunnelHeight = new Integer(argv[4]);
//5: height above tunnel height where trees should be removed
var removeTreeHeight = argv[5];

var editSession;
var localSession;
var selectedRegion;
var parserContext;

editSession = context.remember();

//Get the player's region selection
localSession = context.getSession();
selectedRegion = localSession.getSelection();

//Initialise parserContext
parserContext = new ParserContext();
parserContext.setExtent(new SideEffectExtent(editSession.getWorld()));
parserContext.setSession(localSession);
parserContext.setWorld(editSession.getWorld());
parserContext.setActor(player);

//Define masks
let trackMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[1], parserContext);
let replaceableBlockMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[3], parserContext);
let airMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("minecraft:air", parserContext);
let groundMask = new MaskIntersection(Masks.negate(replaceableBlockMask), Masks.negate(trackMask));

//Set the mask for the blocks that may be replaced
let railMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("mtr:rail", parserContext);
editSession.setMask(Masks.negate(railMask));

//Provide feedback to user
let blocksChanged = 0;
let blocksEvaluated = 0;
let regionSize = selectedRegion.getVolume();
context.print("Creating corridor...");

//Loop through all blocks in selection
let iterator = selectedRegion.iterator();

while (iterator.hasNext()) {
    let point = iterator.next();
    let neighbouringTrackBlocksCount = 0;
    let neighbouringTrackBlocks2D = [
        trackMask.test(point.add(1,0,0)) || trackMask.test(point.add(1,1,0)) || trackMask.test(point.add(1,-1,0)),
        trackMask.test(point.add(0,0,1)) || trackMask.test(point.add(0,1,1)) || trackMask.test(point.add(0,-1,1)),
        trackMask.test(point.add(-1,0,0)) || trackMask.test(point.add(-1,1,0)) || trackMask.test(point.add(-1,-1,0)),
        trackMask.test(point.add(0,0,-1)) || trackMask.test(point.add(0,1,-1)) || trackMask.test(point.add(0,-1,-1)),
        trackMask.test(point.add(1,0,1)) || trackMask.test(point.add(1,1,1)) || trackMask.test(point.add(1,-1,1)),
        trackMask.test(point.add(1,0,-1)) || trackMask.test(point.add(1,1,-1)) || trackMask.test(point.add(1,-1,-1)),
        trackMask.test(point.add(-1,0,-1)) || trackMask.test(point.add(-1,1,-1)) || trackMask.test(point.add(-1,-1,-1)),
        trackMask.test(point.add(-1,0,1)) || trackMask.test(point.add(-1,1,1)) || trackMask.test(point.add(-1,-1,1))
    ];
    for (i = 0; i < neighbouringTrackBlocks2D.length; i++) {
        if (neighbouringTrackBlocks2D[i]) {
            neighbouringTrackBlocksCount += 1;
        }
    }
    let isOnEdge = neighbouringTrackBlocksCount < 8;
    let floorRelativeToCeiling = point.subtract(0, tunnelHeight, 0);
    if (trackMask.test(floorRelativeToCeiling)) {
        let isTouchingGround =
        groundMask.test(point.add(1,1,0)) ||
        groundMask.test(point.add(-1,1,0)) ||
        groundMask.test(point.add(0,1,1)) ||
        groundMask.test(point.add(0,1,-1)) ||
        groundMask.test(point.add(-1,1,-1)) ||
        groundMask.test(point.add(1,1,-1)) ||
        groundMask.test(point.add(1,1,1)) ||
        groundMask.test(point.add(-1,1,1));

        if (groundMask.test(point.add(BlockVector3.UNIT_Y))){
            //Tunnel ceiling
            editSession.setBlock(point, BlockTypes.get(argv[2]).getDefaultState());
            blocksChanged++;
            if (!(trackMask.test(floorRelativeToCeiling.add(BlockVector3.UNIT_X)) && trackMask.test(floorRelativeToCeiling.add(BlockVector3.UNIT_Z)) && trackMask.test(floorRelativeToCeiling.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(floorRelativeToCeiling.add(BlockVector3.UNIT_MINUS_Z)))) {
                editSession.setBlock(point.subtract(0,1,0), BlockTypes.get(argv[2]).getDefaultState());
                blocksChanged++;
            }
        } else if (isTouchingGround) {
            //Tunnel-cutting transition
            editSession.setBlock(point, BlockTypes.get(argv[2]).getDefaultState());
            blocksChanged++;
    
            let i = 0;
            while (true) {  
                i++; 
                wallBlock = point.add(0,i,0);
    
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
                    break;
                }
            }
        } else {
            editSession.setBlock(point, BlockTypes.get("minecraft:air").getDefaultState());
            blocksChanged++;
            //Remove trees above tracks
            for (i = 1; i <= removeTreeHeight; i++) {
                if (!airMask.test(point.add(0,i,0)) && replaceableBlockMask.test(point.add(0,i,0))) {
                    editSession.setBlock(point.add(0,i,0), BlockTypes.get("minecraft:air").getDefaultState());
                    blocksChanged++;
                }
            }
        }
    } else if (trackMask.test(point)) {
        if (isOnEdge) {
            //Tunnel or retaining wall
            let i = 0;
            while (true) {
                i++;
                wallBlock = point.add(0,i,0);

                let wallBlockTouchingGround = 
                    groundMask.test(wallBlock.add(1,0,0)) ||
                    groundMask.test(wallBlock.add(-1,0,0)) ||
                    groundMask.test(wallBlock.add(0,0,1)) ||
                    groundMask.test(wallBlock.add(0,0,-1)) ||
                    groundMask.test(wallBlock.add(-1,0,-1)) ||
                    groundMask.test(wallBlock.add(1,0,-1)) ||
                    groundMask.test(wallBlock.add(1,0,1)) ||
                    groundMask.test(wallBlock.add(-1,0,1));
                
                if ((replaceableBlockMask.test(wallBlock) || i < tunnelHeight) && wallBlockTouchingGround) {
                    editSession.setBlock(wallBlock, BlockTypes.get(argv[2]).getDefaultState());
                    blocksChanged++;
                } else {
                    break;
                }
            }
        } else {
            //Set air blocks between the tunnel ceiling and tunnel floor
            for (i = 1; i < tunnelHeight; i++) {
                if (!airMask.test(point.add(0,i,0))) {
                    editSession.setBlock(point.add(0,i,0), BlockTypes.get("minecraft:air").getDefaultState());
                    blocksChanged++;
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
context.print(new String(blocksChanged).concat(" blocks were changed."));