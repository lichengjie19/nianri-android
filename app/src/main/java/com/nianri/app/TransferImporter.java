package com.nianri.app;

import android.content.Context;

final class TransferImporter {
    private TransferImporter() {
    }

    static TransferMerger.Result apply(Context context, TransferData incoming, boolean replace) {
        EventStore eventStore = new EventStore(context);
        TagStore tagStore = new TagStore(context);
        TransferMerger.Result result = replace
                ? TransferMerger.replace(incoming)
                : TransferMerger.merge(
                        eventStore.load(),
                        eventStore.loadDeleted(),
                        tagStore.load(),
                        incoming
                );
        tagStore.saveForMigration(result.tags);
        eventStore.save(result.events);
        eventStore.saveDeleted(result.deletedEvents);
        return result;
    }
}
