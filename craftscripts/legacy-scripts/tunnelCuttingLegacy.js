//For constructing tunnels and cuttings

importPackage(Packages.java.math);
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
var tunnelHeight = argv[4] + 1;
//5: fencing material
//6: fencing height
var fencingHeight = argv[6];

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

//Material of the track
let trackMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[1], parserContext);

//Set the mask for the blocks that may be replaced
let replaceableBlockMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[3], parserContext);
editSession.setMask(replaceableBlockMask);
let groundMask = Masks.negate(replaceableBlockMask);

//Loop through all blocks in selection
let iterator = selectedRegion.iterator();

while (iterator.hasNext()) {
    let point = iterator.next();
    let isOnEdge = trackMask.test(point) && !(trackMask.test(point.add(BlockVector3.UNIT_X)) && trackMask.test(point.add(BlockVector3.UNIT_Z)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z)) && trackMask.test(point.add(1,0,1)) && trackMask.test(point.add(1,0,-1)) && trackMask.test(point.add(-1,0,-1)) && trackMask.test(point.add(-1,0,1)));

    //Tunnel ceiling
    if (trackMask.test(point.subtract(0, tunnelHeight, 0)) && groundMask.test(point.add(BlockVector3.UNIT_Y))){
        editSession.setBlock(point, BlockTypes.get(argv[2]).getDefaultState());
        if (groundMask.test(point.add(BlockVector3.UNIT_X)) || groundMask.test(point.add(BlockVector3.UNIT_Z)) || groundMask.test(point.add(BlockVector3.UNIT_MINUS_X)) || groundMask.test(point.add(BlockVector3.UNIT_MINUS_Z))) {
            editSession.setBlock(point.subtract(0,1,0), BlockTypes.get(argv[2]).getDefaultState());
        }
    } else if (isOnEdge) {
        //Tunnel or retaining wall
        let i = 0;
        let wallBlock = point;

        while (true) {
            if (replaceableBlockMask.test(wallBlock) && (groundMask.test(wallBlock.add(1,0,0)) || groundMask.test(wallBlock.add(-1,0,0)) || groundMask.test(wallBlock.add(0,0,1)) || groundMask.test(wallBlock.add(0,0,-1)))) {
                wallBlock = wallBlock.add(0,1,0);
                editSession.setBlock(wallBlock, BlockTypes.get(argv[2]).getDefaultState());
            } else if (i < fencingHeight) {
                editSession.setBlock(wallBlock.add(0,i,0), BlockTypes.get(argv[5]).getDefaultState());
                i++;
            } else {
                break;
            }
        }
    } else if (trackMask.test(point.subtract(0, tunnelHeight, 0)) && replaceableBlockMask.test(point.add(BlockVector3.UNIT_Y)) && (groundMask.test(point.add(1,1,0)) || groundMask.test(point.add(0,1,1)) || groundMask.test(point.add(-1,1,0)) || groundMask.test(point.add(0,1,-1)))){
        //Tunnel-cutting transition
        editSession.setBlock(point, BlockTypes.get(argv[2]).getDefaultState());

        let i = 0;
        let wallBlock = point;

        while (true) {
            if (replaceableBlockMask.test(wallBlock) && (groundMask.test(wallBlock.add(1,0,0)) || groundMask.test(wallBlock.add(-1,0,0)) || groundMask.test(wallBlock.add(0,0,1)) || groundMask.test(wallBlock.add(0,0,-1)))) {
                wallBlock = point.add(0,1,0);
                editSession.setBlock(wallBlock, BlockTypes.get(argv[2]).getDefaultState());
            } else if (i < fencingHeight) {
                editSession.setBlock(wallBlock.add(0,i,0), BlockTypes.get(argv[5]).getDefaultState());
                i++;
            } else {
                break;
            }
        }
    }
}