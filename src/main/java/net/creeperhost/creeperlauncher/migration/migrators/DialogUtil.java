package net.creeperhost.creeperlauncher.migration.migrators;

import net.creeperhost.creeperlauncher.api.data.other.OpenModalData;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DialogUtil {
    public static boolean confirmDialog(String title, String body) {
        AtomicBoolean result = new AtomicBoolean();

        OpenModalData.openModal(title, body, List.of(
                new OpenModalData.ModalButton( "Yes", "green", () -> {
                    result.set(true);
                    synchronized (result) {
                        result.notify();
                    }
                }),
                new OpenModalData.ModalButton("No", "red", () -> {
                    result.set(false);
                    synchronized (result) {
                        result.notify();
                    }
                })
        ));

        try {
            synchronized (result) {
                result.wait();
            }
        } catch (InterruptedException ignored) {
        }

        return result.get();
    }
}
