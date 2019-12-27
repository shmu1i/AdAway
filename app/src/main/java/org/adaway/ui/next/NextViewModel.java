package org.adaway.ui.next;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;
import org.adaway.util.AppExecutors;
import org.adaway.util.Log;

/**
 * This class is an {@link AndroidViewModel} for the {@link NextActivity} cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class NextViewModel extends AndroidViewModel {
    private static final String TAG = "NextViewModel";

    private final SourceModel sourceModel;
    private final AdBlockModel adBlockModel;
    private final UpdateModel updateModel;

    private final HostsSourceDao hostsSourceDao;
    private final HostListItemDao hostListItemDao;

    private MutableLiveData<HostError> error;

    public NextViewModel(@NonNull Application application) {
        super(application);
        AdAwayApplication awayApplication = (AdAwayApplication) application;
        this.sourceModel = awayApplication.getSourceModel();
        this.adBlockModel = awayApplication.getAdBlockModel();
        this.updateModel = awayApplication.getUpdateModel();
        AppExecutors.getInstance().networkIO().execute(this.updateModel::checkUpdate);

        AppDatabase database = AppDatabase.getInstance(application);
        this.hostsSourceDao = database.hostsSourceDao();
        this.hostListItemDao = database.hostsListItemDao();

        this.error = new MutableLiveData<>();
    }

    public LiveData<Boolean> isAdBlocked() {
        return this.adBlockModel.isApplied();
    }

    public LiveData<Boolean> isUpdateAvailable() {
        return this.sourceModel.isUpdateAvailable();
    }

    public String getVersionName() {
        return this.updateModel.getVersionName();
    }

    public LiveData<Manifest> getAppManifest() {
        return this.updateModel.getManifest();
    }

    public LiveData<Integer> getBlockedHostCount() {
        return this.hostListItemDao.getBlockedHostCount();
    }

    public LiveData<Integer> getAllowedHostCount() {
        return this.hostListItemDao.getAllowedHostCount();
    }

    public LiveData<Integer> getRedirectHostCount() {
        return this.hostListItemDao.getRedirectHostCount();
    }

    public LiveData<Integer> getUpToDateSourceCount() {
        return this.hostsSourceDao.countUpToDate();
    }

    public LiveData<Integer> getOutdatedSourceCount() {
        return this.hostsSourceDao.countOutdated();
    }

    public LiveData<HostError> getError() {
        return this.error;
    }

    public void toggleAdBlocking() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (Boolean.TRUE == this.adBlockModel.isApplied().getValue()) {
                    this.adBlockModel.revert();
                } else {
                    this.adBlockModel.apply();
                }
            } catch (HostErrorException exception) {
                Log.w(TAG, "Failed to toggle ad blocking.", exception);
                this.error.postValue(exception.getError());
            }
        });
    }

    public void update() {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                this.sourceModel.checkForUpdate();
            } catch (HostErrorException exception) {
                Log.w(TAG, "Failed to update.", exception);
                this.error.postValue(exception.getError());
            }
        });
    }

    public void sync() {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                this.sourceModel.retrieveHostsSources();
                this.adBlockModel.apply();
            } catch (HostErrorException exception) {
                Log.w(TAG, "Failed to sync.", exception);
                this.error.postValue(exception.getError());
            }
        });
    }

    public void enableAllSources() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            if (this.sourceModel.enableAllSources()) {
                sync();
            }
        });
    }
}
