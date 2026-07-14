package com.transiva.app.driver.presentation;

import android.os.Handler;
import android.os.Looper;

import com.transiva.app.driver.domain.DriverDashboardRepository;
import com.transiva.app.driver.domain.DriverDashboardState;
import com.transiva.app.driver.domain.DriverOrder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DriverDashboardPresenter {

    private final DriverDashboardRepository repository;
    private DriverDashboardContract.View view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean actionRunning = new AtomicBoolean(false);

    public DriverDashboardPresenter(
            DriverDashboardRepository repository,
            DriverDashboardContract.View view
    ) {
        this.repository = repository;
        this.view = view;
    }

    public void load(boolean showLoading) {
        if (!loading.compareAndSet(false, true)) return;
        if (showLoading && view != null) view.showLoading(true);

        repository.loadDashboard(new DriverDashboardRepository.DashboardCallback() {
            @Override public void onSuccess(DriverDashboardState state) {
                main.post(() -> {
                    loading.set(false);
                    if (view == null) return;
                    view.showLoading(false);
                    view.showDashboard(state);
                });
            }

            @Override public void onError(int httpCode, String code, String message) {
                main.post(() -> {
                    loading.set(false);
                    if (view == null) return;
                    view.showLoading(false);
                    if (httpCode == 401 || httpCode == 403
                            || "UNAUTHORIZED".equalsIgnoreCase(code)
                            || "SESSION_EXPIRED".equalsIgnoreCase(code)) {
                        view.showSessionExpired();
                    } else {
                        view.showMessage(message);
                    }
                });
            }
        });
    }

    public void setOnline(boolean online, String driverType) {
        if (!actionRunning.compareAndSet(false, true)) return;
        if (view != null) view.showActionLoading("status", true);

        repository.setOnline(online, driverType,
                new DriverDashboardRepository.ActionCallback() {
                    @Override public void onSuccess(String message, DriverOrder order) {
                        main.post(() -> {
                            actionRunning.set(false);
                            if (view == null) return;
                            view.showActionLoading("status", false);
                            view.showMessage(message);
                            load(false);
                        });
                    }

                    @Override public void onError(
                            int httpCode, String code, String message) {
                        main.post(() -> {
                            actionRunning.set(false);
                            if (view == null) return;
                            view.showActionLoading("status", false);
                            if (httpCode == 401 || httpCode == 403) {
                                view.showSessionExpired();
                            } else {
                                view.showMessage(message);
                                load(false);
                            }
                        });
                    }
                });
    }

    public void acceptOrder(String orderId) {
        if (!actionRunning.compareAndSet(false, true)) return;
        if (view != null) view.showActionLoading("accept:" + orderId, true);

        repository.acceptOrder(
                orderId,
                UUID.randomUUID().toString(),
                new DriverDashboardRepository.ActionCallback() {
                    @Override public void onSuccess(String message, DriverOrder order) {
                        main.post(() -> {
                            actionRunning.set(false);
                            if (view == null) return;
                            view.showActionLoading("accept:" + orderId, false);
                            view.showMessage(message);
                            if (order != null) view.openTrip(order);
                            else load(false);
                        });
                    }

                    @Override public void onError(
                            int httpCode, String code, String message) {
                        main.post(() -> {
                            actionRunning.set(false);
                            if (view == null) return;
                            view.showActionLoading("accept:" + orderId, false);
                            if (httpCode == 401 || httpCode == 403) {
                                view.showSessionExpired();
                            } else {
                                view.showMessage(message);
                                load(false);
                            }
                        });
                    }
                }
        );
    }

    public void destroy() {
        view = null;
        main.removeCallbacksAndMessages(null);
        repository.destroy();
    }
}
