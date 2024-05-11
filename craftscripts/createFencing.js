//For constructing fencing

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
//2: blocks that may be replaced
//3: fencing material
//4: fencing height
const fencingHeight = new Integer(argv[4]);

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
let replaceableBlockMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[2], parserContext);
editSession.setMask(replaceableBlockMask);

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
    let isOnEdge;
    if (neighbouringTrackBlocksCount < 8) {
        isOnEdge = true;
    } else {
        isOnEdge = false;
    }

    if (trackMask.test(point) && replaceableBlockMask.test(point.add(0,1,0)) && ((trackMask.test(point.subtract(0,1,0)) && trackMask.test(point.subtract(0,2,0))) || isOnEdge)) {
        let fencingHeightBonus;
        if (neighbouringTrackBlocksCount == 2) {
            fencingHeightBonus = 2;
        } else {
            fencingHeightBonus = 1;
        }
        for (i = 1; i < fencingHeight + fencingHeightBonus; i++) {
            editSession.setBlock(point.add(0,i,0), BlockTypes.get(argv[3]).getDefaultState());
        }
    }
}