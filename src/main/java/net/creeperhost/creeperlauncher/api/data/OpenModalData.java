package net.creeperhost.creeperlauncher.api.data;

import net.creeperhost.creeperlauncher.Settings;

import java.util.List;
import java.util.UUID;

public class OpenModalData extends BaseData
{
    private final String title;
    private final String message;
    public final List<ModalButton> buttons;
    public static OpenModalData currentlyOpenModal = null;
    public String id;

    public OpenModalData(String title, String message, List<ModalButton> buttons) {
        this.type = "openModal";
        this.title = title;
        this.message = message;
        this.buttons = buttons;
    }

    public static void openModal(String title, String message, List<ModalButton> modalButtons) {
        currentlyOpenModal = new OpenModalData(title, message, modalButtons);
        Settings.webSocketAPI.sendMessage(currentlyOpenModal);
    }

    public static class ModalButton
    {
        public final String message;
        private final String name;
        private final String colour;
        public final Runnable callback;

        public ModalButton(String name, String colour, Runnable callback) {
            this(UUID.randomUUID().toString(), name, colour, callback);
        }

        public ModalButton(String message, String name, String colour, Runnable callback) {
            this.message = message;
            this.name = name;
            this.colour = colour;
            this.callback = callback;
        }
    }

    public static class ModalCallbackData extends BaseData
    {
        public String id;
        public String message;
    }
}
