package com.myapp.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.accounts.Account;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.myapp.myapplication.model.DeveloperAccount;
import com.myapp.myapplication.sync.AutosyncHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = LoginActivity.class.getSimpleName();
    public static final String EXTRA_MANAGE_ACCOUNTS_MODE = "com.myapp.myapplication.manageAccounts";
    public static final String AUTH_TOKEN_TYPE_ANDROID_DEVELOPER = "androiddeveloper";

    protected static final int CREATE_ACCOUNT_REQUEST = 1;

    private List<DeveloperAccount> developerAccounts;

    private boolean manageAccountsMode = false;
    private boolean blockGoingBack = false;
    private DeveloperAccount selectedAccount = null;
    private View okButton;
    private LinearLayout accountList;

    private AccountManager accountManager;
    private DeveloperAccountManager developerAccountManager;
    private AutosyncHandler syncHandler;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        accountManager = AccountManager.get(this);
        developerAccountManager = DeveloperAccountManager.getInstance(getApplicationContext());
        syncHandler = new AutosyncHandler();

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            manageAccountsMode = extras.getBoolean(LoginActivity.EXTRA_MANAGE_ACCOUNTS_MODE);
        }

        setProgressBarIndeterminateVisibility(false);

        if (manageAccountsMode) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.manage_accounts);
        }

        accountList = (LinearLayout) findViewById(R.id.login_input);


        okButton = findViewById(R.id.login_ok_button);
        okButton.setClickable(true);
        okButton.setOnClickListener(v->{
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected void onPreExecute() {
                    setProgressBarIndeterminateVisibility(true);
                    okButton.setEnabled(false);
                }

                @Override
                protected Void doInBackground(Void... args) {
                    saveDeveloperAccounts();

                    return null;
                }

                @Override
                protected void onPostExecute(Void arg) {
                    setProgressBarIndeterminateVisibility(false);
                    okButton.setEnabled(true);

                    if (selectedAccount != null) {
                        redirectToMain(selectedAccount.getName(),
                                selectedAccount.getDeveloperId());
                    } else {
                        // Go to the first non hidden account
                        for (DeveloperAccount account : developerAccounts) {
                            if (account.isVisible()) {
                                redirectToMain(account.getName(), account.getDeveloperId());
                                break;
                            }
                        }
                    }
                }
            }.execute();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean skipAutologin = Preferences.getSkipAutologin(this);

        if (!manageAccountsMode & !skipAutologin & selectedAccount != null) {
            redirectToMain(selectedAccount.getName(), selectedAccount.getDeveloperId());
        } else {
            showAccountList();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.login_menu, menu);
        return true;
    }


    /**
     * Called if item in option menu is selected.
     *
     * @param item
     *            The chosen menu item
     * @return boolean true/false
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.itemLoginmenuAdd:
                addNewGoogleAccount();
                break;
            case android.R.id.home:
                if (!blockGoingBack) {
                    setResult(RESULT_OK);
                    finish();
                }
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        setResult(blockGoingBack ? RESULT_CANCELED : RESULT_OK);
        super.onBackPressed();
    }


    protected void showAccountList() {
        Account[] googleAccounts = accountManager.getAccountsByType(AutosyncHandler.ACCOUNT_TYPE_GOOGLE);
        List<DeveloperAccount> dbAccounts = developerAccountManager.getAllDeveloperAccounts();
        developerAccounts = new ArrayList<DeveloperAccount>();

        accountList.removeAllViews();
        for (int i = 0; i < googleAccounts.length; i++) {
            DeveloperAccount developerAccount = DeveloperAccount
                    .createHidden(googleAccounts[i].name);
            int idx = dbAccounts.indexOf(developerAccount);
            // use persistent object if exists
            if (idx != -1) {
                developerAccount = dbAccounts.get(idx);
            }
            developerAccounts.add(developerAccount);

            // Setup auto sync
            // only do this when managing accounts, otherwise sync may start
            // in the background before accounts are actually configured
            if (manageAccountsMode) {
                // Ensure it matches the sync period (excluding disabled state)
                syncHandler.setAutosyncPeriod(googleAccounts[i].name,
                        Preferences.getLastNonZeroAutosyncPeriod(this));
                // Now make it match the master sync (including disabled state)
                syncHandler.setAutosyncPeriod(googleAccounts[i].name,
                        Preferences.getAutosyncPeriod(this));
            }

            View accountItem = getLayoutInflater().inflate(R.layout.login_list_item, null);
            TextView accountName = (TextView) accountItem.findViewById(R.id.login_list_item_text);
            accountName.setText(googleAccounts[i].name);
            accountItem.setTag(developerAccount);
            CheckBox enabled = (CheckBox) accountItem.findViewById(R.id.login_list_item_enabled);
            enabled.setChecked(!developerAccount.isHidden());
            enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    DeveloperAccount account = (DeveloperAccount) ((View) buttonView.getParent())
                            .getTag();
                    if (isChecked) {
                        account.activate();
                    } else {
                        account.hide();
                    }

                    if (manageAccountsMode && account.equals(selectedAccount)) {
                        // If they remove the current account, then stop them
                        // going back
                        blockGoingBack = account.isHidden();
                    }

                    okButton.setEnabled(isAtLeastOneAccountEnabled());
                }
            });
            accountList.addView(accountItem);
        }

        // Update ok button
        okButton.setEnabled(isAtLeastOneAccountEnabled());
    }

    private void saveDeveloperAccounts() {
        for (DeveloperAccount account : developerAccounts) {
            if (account.isHidden()) {
                // They are removing the account from Andlytics, disable
                // syncing
                syncHandler.setAutosyncEnabled(account.getName(), false);
            } else {
                // Make it match the master sync period (including
                // disabled state)
                syncHandler.setAutosyncPeriod(account.getName(),
                        Preferences.getAutosyncPeriod(LoginActivity.this));
            }
            developerAccountManager.addOrUpdateDeveloperAccount(account);
        }
    }

    private boolean isAtLeastOneAccountEnabled() {
        for (DeveloperAccount acc : developerAccounts) {
            if (acc.isVisible()) {
                return true;
            }
        }

        return false;
    }

    private void addNewGoogleAccount() {
        AccountManagerCallback<Bundle> callback = new AccountManagerCallback<Bundle>() {
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Bundle bundle = future.getResult();
                    bundle.keySet();
                    Log.d(TAG, "account added: " + bundle);

                    showAccountList();

                } catch (OperationCanceledException e) {
                    Log.d(TAG, "addAccount was canceled");
                } catch (IOException e) {
                    Log.d(TAG, "addAccount failed: " + e);
                } catch (AuthenticatorException e) {
                    Log.d(TAG, "addAccount failed: " + e);
                }
                // gotAccount(false);
            }
        };

        // TODO request a weblogin: token here, so we have it cached?
        accountManager.addAccount(AutosyncHandler.ACCOUNT_TYPE_GOOGLE,
                LoginActivity.AUTH_TOKEN_TYPE_ANDROID_DEVELOPER, null, null /* options */,
                LoginActivity.this, callback, null /* handler */);
    }

    private void redirectToMain(String selectedAccount, String developerId) {
        Preferences.saveSkipAutoLogin(this, false);
        /*Intent intent = new Intent(MainActivity.this, Main.class);
        intent.putExtra(BaseActivity.EXTRA_AUTH_ACCOUNT_NAME, selectedAccount);
        intent.putExtra(BaseActivity.EXTRA_DEVELOPER_ID, developerId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.activity_fade_in, R.anim.activity_fade_out);
        finish();*/
    }

}