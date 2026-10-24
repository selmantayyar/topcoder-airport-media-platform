package com.airportmedia.platform.service;

import com.airportmedia.platform.domain.model.output.ServerEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * The one live push channel. Any component emits {@link ServerEvent}s here; every
 * connected SSE client receives them, so an injected event reaches all role views at once.
 */
@ApplicationScoped
public class EventStream {

    private final BroadcastProcessor<ServerEvent> processor = BroadcastProcessor.create();
    private final TransactionSynchronizationRegistry transactions;

    public EventStream(TransactionSynchronizationRegistry transactions) {
        this.transactions = transactions;
    }

    public void emit(ServerEvent event) {
        processor.onNext(event);
    }

    /**
     * Emit once the current transaction commits (immediately if there is none), so a client that
     * refetches on the event never reads pre-commit data. Rolled-back work emits nothing.
     */
    public void emitAfterCommit(ServerEvent event) {
        if (transactions.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            emit(event);
            return;
        }
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {}

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    emit(event);
                }
            }
        });
    }

    public Multi<ServerEvent> multi() {
        return processor;
    }
}
