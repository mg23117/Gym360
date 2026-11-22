package com.example.gym360.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.gym360.data.local.dao.MembershipDao;
import com.example.gym360.data.local.dao.MembershipPurchaseDao;
import com.example.gym360.data.local.dao.StoreDao;
import com.example.gym360.data.local.dao.UserDao;
import com.example.gym360.data.local.dao.UserMembershipSummaryDao;
import com.example.gym360.data.local.dao.WalletSnapshotDao;
import com.example.gym360.data.local.entity.MembershipEntity;
import com.example.gym360.data.local.entity.MembershipPurchaseEntity;
import com.example.gym360.data.local.entity.StoreCartItemEntity;
import com.example.gym360.data.local.entity.StoreProductEntity;
import com.example.gym360.data.local.entity.UserEntity;
import com.example.gym360.data.local.entity.UserMembershipSummaryEntity;
import com.example.gym360.data.local.entity.WalletSnapshotEntity;

@Database(entities = {
        MembershipEntity.class,
        UserEntity.class,
        WalletSnapshotEntity.class,
        UserMembershipSummaryEntity.class,
        MembershipPurchaseEntity.class,
        StoreProductEntity.class,
        StoreCartItemEntity.class
}, version = 7, exportSchema = true)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract MembershipDao membershipDao();
    public abstract UserDao userDao();
    public abstract StoreDao storeDao();
    public abstract WalletSnapshotDao walletSnapshotDao();
    public abstract UserMembershipSummaryDao userMembershipSummaryDao();
    public abstract MembershipPurchaseDao membershipPurchaseDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE memberships ADD COLUMN localRevision INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `users` (" +
                    "`uid` TEXT NOT NULL, " +
                    "`fullName` TEXT NOT NULL, " +
                    "`email` TEXT NOT NULL, " +
                    "PRIMARY KEY(`uid`))");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE memberships ADD COLUMN durationDays INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallet_snapshots` (" +
                    "`uid` TEXT NOT NULL, `balanceCents` INTEGER NOT NULL, " +
                    "`cachedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`uid`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `user_membership_summaries` (" +
                    "`uid` TEXT NOT NULL, `activePlanId` TEXT NOT NULL, " +
                    "`activePlanName` TEXT NOT NULL, `activePlanWeightDays` INTEGER NOT NULL, " +
                    "`expiresOnExclusive` TEXT NOT NULL, `cachedAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`uid`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `membership_purchases` (" +
                    "`id` TEXT NOT NULL, `uid` TEXT NOT NULL, `membershipId` TEXT NOT NULL, " +
                    "`membershipName` TEXT NOT NULL, `daysGranted` INTEGER NOT NULL, " +
                    "`priceCents` INTEGER NOT NULL, `purchasedAtMillis` INTEGER NOT NULL, " +
                    "`expiresOnExclusiveAfter` TEXT NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_membership_purchases_uid` ON " +
                    "`membership_purchases` (`uid`)");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `store_products` (" +
                    "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL," +
                    "`description` TEXT NOT NULL, `priceCents` INTEGER NOT NULL," +
                    "`stock` INTEGER NOT NULL, `imagesJson` TEXT NOT NULL," +
                    "`isActive` INTEGER NOT NULL, `cachedAtMillis` INTEGER NOT NULL," +
                    "PRIMARY KEY(`id`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `store_cart_items` (" +
                    "`id` TEXT NOT NULL, `uid` TEXT NOT NULL," +
                    "`productId` TEXT NOT NULL, `quantity` INTEGER NOT NULL," +
                    "PRIMARY KEY(`id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_store_cart_items_uid` " +
                    "ON `store_cart_items` (`uid`)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "gym360_database"
                    ).addMigrations(MIGRATION_1_2,
                                    MIGRATION_2_3,
                                    MIGRATION_3_4,
                                    MIGRATION_4_5,
                                    MIGRATION_5_6,
                                    MIGRATION_6_7)
                            .build();
                }
            }
        }
        return instance;
    }
}