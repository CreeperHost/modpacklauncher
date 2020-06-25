package net.creeperhost.creeperlauncher.api.handlers;

import net.creeperhost.creeperlauncher.api.data.OpenModalData;

public class ModalCallbackHandler implements IMessageHandler<OpenModalData.ModalCallbackData> {
    @Override
    public void handle(OpenModalData.ModalCallbackData data) {
        String id = data.id;
        String message = data.message;
        if (OpenModalData.currentlyOpenModal != null && OpenModalData.currentlyOpenModal.id.equals(id))
        {
            for(OpenModalData.ModalButton button : OpenModalData.currentlyOpenModal.buttons)
            {
                if (button.message.equals(message))
                {
                    button.callback.run();
                    break;
                }
            }
            OpenModalData.currentlyOpenModal = null;
        }
    }
}