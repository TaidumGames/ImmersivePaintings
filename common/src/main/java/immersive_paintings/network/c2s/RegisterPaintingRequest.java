package immersive_paintings.network.c2s;

import immersive_paintings.Config;
import immersive_paintings.Main;
import immersive_paintings.cobalt.network.Message;
import immersive_paintings.cobalt.network.NetworkHandler;
import immersive_paintings.network.s2c.PaintingListMessage;
import immersive_paintings.network.s2c.RegisterPaintingResponse;
import immersive_paintings.resources.ByteImage;
import immersive_paintings.resources.Painting;
import immersive_paintings.resources.ServerPaintingManager;
import immersive_paintings.util.Utils;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.query.OptionKey;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.luckperms.api.LuckPerms;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public class RegisterPaintingRequest extends Message {
    private final String name;
    private final NbtCompound painting;

    public RegisterPaintingRequest(String name, Painting painting) {
        this.name = name;
        this.painting = painting.toNbt();
    }

    public RegisterPaintingRequest(PacketByteBuf b) {
        this.name = b.readString();
        this.painting = b.readNbt();
    }

    @Override
    public void encode(PacketByteBuf b) {
        b.writeString(name);
        b.writeNbt(painting);
    }

    public boolean isPlayerInGroupOrHigher(PlayerEntity player, String groupName) {
        Logger LOGGER = Main.LOGGER;
        LOGGER.info("Checking if player is in group");
        LuckPerms luckPerms = LuckPermsProvider.get();
        if (luckPerms == null) {
            return false;
        }

        UserManager userManager = luckPerms.getUserManager();
        User user = userManager.getUser(player.getUuid());
        if(user != null){
            return user.resolveDistinctInheritedNodes(QueryOptions.defaultContextualOptions()).stream().anyMatch(node -> node.getKey().equals("group."+groupName));
        }


        return false;
    }
    @Override
    public void receive(PlayerEntity e) {
        Logger LOGGER = Main.LOGGER;
        ByteImage image = UploadPaintingRequest.uploadedImages.get(e.getUuidAsString());
        LOGGER.info("got image");
        boolean hasPerms = isPlayerInGroupOrHigher(e, "donator-1");
        if (hasPerms == false) {
            error("no_permission", e, null);
            return;
        }

        if (image.getWidth() > Config.getInstance().maxUserImageWidth || image.getHeight() > Config.getInstance().maxUserImageHeight) {
            error("too_large", e, null);
            return;
        }

        long count = ServerPaintingManager.get().getCustomServerPaintings().values().stream().filter(p -> p.author.equals(e.getGameProfile().getName())).count();
        if (count > Config.getInstance().maxUserImages) {
            error("limit_reached", e, null);
            return;
        }

        String id = Utils.escapeString(e.getGameProfile().getName()) + "/" + Utils.escapeString(name);
        Identifier identifier = Main.locate(id);

        NbtCompound nbt = this.painting;

        nbt.putString("author", e.getGameProfile().getName());
        nbt.putString("name", name);

        Painting painting = Painting.fromNbt(nbt);

        painting.texture.image = image;

        ServerPaintingManager.registerPainting(
                identifier,
                painting
        );

        //update clients
        for (ServerPlayerEntity player : Objects.requireNonNull(e.getServer()).getPlayerManager().getPlayerList()) {
            NetworkHandler.sendToPlayer(new PaintingListMessage(identifier, painting), player);
        }

        error("", e, identifier);
    }

    private void error(String error, PlayerEntity e, Identifier i) {
        NetworkHandler.sendToPlayer(new RegisterPaintingResponse(error, i), (ServerPlayerEntity)e);
    }
}
