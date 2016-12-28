package com.davidmiguel.gobees.data.source.local;

import android.support.annotation.NonNull;

import com.davidmiguel.gobees.data.model.Apiary;
import com.davidmiguel.gobees.data.model.Hive;
import com.davidmiguel.gobees.data.model.Record;
import com.davidmiguel.gobees.data.model.Recording;
import com.davidmiguel.gobees.data.source.GoBeesDataSource;
import com.davidmiguel.gobees.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Concrete implementation of a data source as a Realm db.
 */
public class GoBeesLocalDataSource implements GoBeesDataSource {

    private static GoBeesLocalDataSource INSTANCE;
    private Realm realm;


    private GoBeesLocalDataSource() {
    }

    public static GoBeesLocalDataSource getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GoBeesLocalDataSource();
        }
        return INSTANCE;
    }

    @Override
    public void openDb() {
        realm = Realm.getDefaultInstance();
    }

    @Override
    public void closeDb() {
        realm.close();
    }

    @Override
    public void deleteAll() {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                realm.deleteAll();
            }
        });
    }

    @Override
    public void getApiaries(@NonNull GetApiariesCallback callback) {
        try {
            RealmResults<Apiary> apiaries = realm.where(Apiary.class).findAll();
            callback.onApiariesLoaded(new ArrayList<>(apiaries));
        } catch (Exception e) {
            callback.onDataNotAvailable();
        }
    }

    @Override
    public void getApiary(long apiaryId, @NonNull GetApiaryCallback callback) {
        try {
            Apiary apiary = realm.where(Apiary.class).equalTo("id", apiaryId).findFirst();
            callback.onApiaryLoaded(apiary);
        } catch (Exception e) {
            callback.onDataNotAvailable();
        }
    }

    @Override
    public void saveApiary(@NonNull final Apiary apiary, @NonNull TaskCallback callback) {
        try {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Save apiary
                    realm.copyToRealmOrUpdate(apiary);
                }
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void refreshApiaries() {
        // Not required because the GoBeesRepository handles the logic of refreshing the
        // data from all the available data sources
    }

    @Override
    public void deleteApiary(@NonNull final Apiary apiary, @NonNull TaskCallback callback) {
        try {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    if (apiary.getHives() != null) {
                        for (Hive hive : apiary.getHives()) {
                            // Delete records of the hives
                            if (hive.getRecords() != null) {
                                hive.getRecords().where().findAll().deleteAllFromRealm();
                            }
                            // Delete hives
                            hive.deleteFromRealm();
                        }
                    }
                    // Delete apiary
                    apiary.deleteFromRealm();
                }
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void deleteAllApiaries(@NonNull TaskCallback callback) {
        try {
            final RealmResults<Apiary> apiaries = realm.where(Apiary.class).findAll();
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Delete all apiaries
                    apiaries.deleteAllFromRealm();
                }
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void getNextApiaryId(@NonNull GetNextApiaryIdCallback callback) {
        Number nextId = realm.where(Apiary.class).max("id");
        callback.onNextApiaryIdLoaded(nextId != null ? nextId.longValue() + 1 : 0);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void getHives(long apiaryId, @NonNull GetHivesCallback callback) {
        try {
            Apiary apiary = realm.where(Apiary.class).equalTo("id", apiaryId).findFirst();
            callback.onHivesLoaded(new ArrayList<>(apiary.getHives()));
        } catch (Exception e) {
            callback.onDataNotAvailable();
        }
    }

    @Override
    public void getHive(long hiveId, @NonNull GetHiveCallback callback) {
        try {
            Hive hive = realm.where(Hive.class).equalTo("id", hiveId).findFirst();
            callback.onHiveLoaded(hive);
        } catch (Exception e) {
            callback.onDataNotAvailable();
        }
    }

    @Override
    public void getHiveWithRecordings(long hiveId, @NonNull GetHiveCallback callback) {
        try {
            // Get hive
            Hive hive = realm.where(Hive.class).equalTo("id", hiveId).findFirst();
            if (hive == null || hive.getRecords() == null) {
                callback.onDataNotAvailable();
                return;
            }
            // Get records
            RealmResults<Record> records = hive.getRecords().where().findAll().sort("timestamp");
            // Clasify records by date into recordings
            Date day;                   // Actual date of the recording
            Date nextDay = new Date(0); // Next day to the recording
            RealmResults<Record> filteredRecords;
            List<Recording> recordings = new ArrayList<>();
            while (true) {
                // Get all records greather than last recordings
                records = records.where().greaterThanOrEqualTo("timestamp", nextDay).findAll();
                if (records.isEmpty()) {
                    break;
                }
                // Get range of days to filter
                day = DateTimeUtils.getDateOnly(records.first().getTimestamp());
                nextDay = DateTimeUtils.getNextDay(day);
                // Filter records of that date and create recording
                filteredRecords = records.where()
                        .greaterThanOrEqualTo("timestamp", day)
                        .lessThan("timestamp", DateTimeUtils.getNextDay(nextDay))
                        .findAll();
                // Create recording
                recordings.add(new Recording(day, new ArrayList<>(filteredRecords)));
            }
            // Set recordings to hive
            hive.setRecordings(recordings);
            // Return hive
            callback.onHiveLoaded(hive);
        } catch (Exception e) {
            callback.onDataNotAvailable();
        }
    }

    @Override
    public void refreshHives(long apiaryId) {
        // Not required because the GoBeesRepository handles the logic of refreshing the
        // data from all the available data sources
    }

    @Override
    public void saveHive(final long apiaryId, @NonNull final Hive hive, @NonNull TaskCallback callback) {
        try {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Save hive
                    realm.copyToRealmOrUpdate(hive);
                    // Add to apiary
                    Apiary apiary = realm.where(Apiary.class).equalTo("id", apiaryId).findFirst();
                    apiary.addHive(hive);
                }
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void deleteHive(@NonNull final Hive hive, @NonNull TaskCallback callback) {
        try {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Delete records of the hive
                    if (hive.getRecords() != null) {
                        hive.getRecords().where().findAll().deleteAllFromRealm();
                    }
                    // Delete hive
                    hive.deleteFromRealm();
                }
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void getNextHiveId(@NonNull GetNextHiveIdCallback callback) {
        Number nextId = realm.where(Hive.class).max("id");
        callback.onNextHiveIdLoaded(nextId != null ? nextId.longValue() + 1 : 0);
    }

    @Override
    public void saveRecord(final long hiveId, @NonNull final Record record, @NonNull TaskCallback callback) {
        try {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Save record
                    realm.copyToRealmOrUpdate(record);
                    // Add to hive
                    Hive hive = realm.where(Hive.class).equalTo("id", hiveId).findFirst();
                    hive.addRecord(record);
                }
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void saveRecords(final long hiveId, @NonNull final List<Record> records,
                            @NonNull TaskCallback callback) {
        try {
            // Get first id
            Number n = realm.where(Record.class).max("id");
            long nextId = n != null ? n.longValue() + 1 : 0;
            // Set ids
            for (Record r : records) {
                r.setId(nextId++);
            }
            // Save records
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    // Save records
                    for (Record r : records) {
                        realm.copyToRealmOrUpdate(r);
                    }
                    // Add to hive
                    Hive hive = realm.where(Hive.class).equalTo("id", hiveId).findFirst();
                    hive.addRecords(records);
                }
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void getRecording(long hiveId, Date start, Date end, @NonNull GetRecordingCallback callback) {
        // Get hive
        Hive hive = realm.where(Hive.class).equalTo("id", hiveId).findFirst();
        if (hive == null || hive.getRecords() == null) {
            callback.onDataNotAvailable();
            return;
        }
        // Get records
        RealmResults<Record> records = hive.getRecords()
                .where()
                .greaterThanOrEqualTo("timestamp", DateTimeUtils.setTime(start, 0, 0, 0, 0))
                .lessThanOrEqualTo("timestamp", DateTimeUtils.setTime(end, 23, 59, 59, 999))
                .findAll()
                .sort("timestamp");
        // Create recording
        Recording recording = new Recording(start, new ArrayList<>(records));
        callback.onRecordingLoaded(recording);
    }

    @Override
    public void deleteRecording(long hiveId, @NonNull Recording recording, @NonNull TaskCallback callback) {
        try {
            // Get hive
            Hive hive = realm.where(Hive.class).equalTo("id", hiveId).findFirst();
            if (hive == null) {
                callback.onFailure();
                return;
            }
            if (hive.getRecords() != null) {
                // Get records to delete
                final RealmResults<Record> records;
                records = hive.getRecords()
                        .where()
                        .greaterThanOrEqualTo("timestamp", DateTimeUtils.setTime(recording.getDate(), 0, 0, 0, 0))
                        .lessThanOrEqualTo("timestamp", DateTimeUtils.setTime(recording.getDate(), 23, 59, 59, 999))
                        .findAll();
                // Delete records
                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        records.deleteAllFromRealm();
                    }
                });
            }
            callback.onSuccess();
        } catch (Exception e) {
            callback.onFailure();
        }
    }

    @Override
    public void refreshRecordings(long hiveId) {
        // Not required because the GoBeesRepository handles the logic of refreshing the
        // data from all the available data sources
    }
}
