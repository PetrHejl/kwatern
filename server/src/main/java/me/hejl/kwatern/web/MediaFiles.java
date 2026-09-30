package me.hejl.kwatern.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Media;

/**
 * Images and files of the tree: portraits, thumbnails, the larger image of a media page and the file itself, all
 * made from the media objects of the view the visitor sees, never from a path in the address.
 */
final class MediaFiles {

    private MediaFiles() {}

    /** Whether the address is one of an image or file of the tree. */
    static boolean handles(String path) {
        return path.startsWith("/portrait/")
                || path.startsWith("/thumbnail/")
                || path.matches("/media/[^/]+/(image|original)");
    }

    /** Serves an address for which {@link #handles} holds. */
    static void serve(Request request) throws IOException {
        // "portrait", "thumbnail" or "media", and the rest.
        String[] parts = request.path().substring(1).split("/", 2);
        Site site = request.site();
        GrampsDatabase db = site.data().database();
        MediaImages images = site.images();
        switch (parts[0]) {
            case "portrait" ->
                sendJpeg(
                        request,
                        Request.find(db.people(), parts[1])
                                .filter(p -> !site.data().isLiving(p.handle()))
                                .flatMap(images::portraitImage));
            case "thumbnail" ->
                sendJpeg(request, Request.find(db.media(), parts[1]).flatMap(images::thumbnail));
            default -> {
                // ID/image or ID/original
                String[] rest = parts[1].split("/");
                Optional<Media> media = Request.find(db.media(), rest[0]);
                if (rest[1].equals("image")) {
                    sendJpeg(request, media.flatMap(images::display));
                } else {
                    original(request, media);
                }
            }
        }
    }

    private static void sendJpeg(Request request, Optional<MediaImages.Jpeg> jpeg) throws IOException {
        if (jpeg.isEmpty()) {
            Responses.empty(request.exchange(), 404);
            return;
        }
        Responses.imageCaching(request.exchange(), request.members());
        if (Responses.notModified(request.exchange(), jpeg.get().etag())) {
            return;
        }
        Responses.send(request.exchange(), 200, "image/jpeg", jpeg.get().data());
    }

    private static void original(Request request, Optional<Media> media) throws IOException {
        Optional<MediaImages.Original> original = media.flatMap(request.site().images()::original);
        if (original.isEmpty()) {
            Responses.empty(request.exchange(), 404);
            return;
        }
        // Signed in, the browser checks each time: without a tag, it fetched the whole file again.
        var attributes = Files.readAttributes(original.get().file(), BasicFileAttributes.class);
        String etag = "\"%s-%d-%d-%d\""
                .formatted(
                        media.get().handle(),
                        media.get().change(),
                        attributes.size(),
                        attributes.lastModifiedTime().toMillis());
        Responses.file(
                request.exchange(),
                original.get().file(),
                attributes.size(),
                original.get().contentType(),
                etag,
                request.members());
    }
}
