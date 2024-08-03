importPackage(Packages.java.io);
importPackage(Packages.com.sk89q.worldedit);
importPackage(Packages.com.sk89q.worldedit.math);
importPackage(Packages.com.sk89q.worldedit.session);
importPackage(Packages.com.sk89q.worldedit.extension.input);
importPackage(Packages.com.sk89q.worldedit.extent.world);
importPackage(Packages.com.sk89q.worldedit.extent.clipboard.io);
importPackage(Packages.com.sk89q.worldedit.function.operation);

//argv:
//1: material of track bed
//2: blocks that may be replaced by the embankment

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
trackMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[1], parserContext);

//Set the mask for the blocks that may be replaced
replaceableBlockMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[2], parserContext);
editSession.setMask(replaceableBlockMask);

//Loop through all blocks in selection
let iterator = selectedRegion.iterator();

while (iterator.hasNext()) {
    let point = iterator.next();
    let file;
    let worldEdit = WorldEdit.getInstance();

    let config = worldEdit.getConfiguration();
    let dir = worldEdit.getWorkingDirectoryPath(config.saveDir).toFile();

    if (trackMask.test(point)) {
        isOnEdge = !(trackMask.test(point.add(BlockVector3.UNIT_X)) && trackMask.test(point.add(BlockVector3.UNIT_Z)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z)));
        if (isOnEdge) {
            //Load column schematic
            file = WorldEdit.getInstance().getSafeOpenFile(player, dir, "column", BuiltInClipboardFormat.SPONGE_SCHEMATIC.getPrimaryFileExtension(), ClipboardFormats.getFileExtensionArray());
        } else {
            //Load pyramid schematic
            file = WorldEdit.getInstance().getSafeOpenFile(player, dir, "pyramid", BuiltInClipboardFormat.SPONGE_SCHEMATIC.getPrimaryFileExtension(), ClipboardFormats.getFileExtensionArray());
        }
        //Paste schematic
        let format = ClipboardFormats.findByFile(file);
        let reader = format.getReader(new FileInputStream(file));
        let clipboard = reader.read();
        let holder = new ClipboardHolder(clipboard);
        let operation = holder.createPaste(editSession).to(point).ignoreAirBlocks(true).copyBiomes(false).copyEntities(false).maskSource(null).build();
        Operations.complete(operation);
    }
}