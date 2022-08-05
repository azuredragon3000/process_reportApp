package com.myapp.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.accounts.AccountManager;
import android.os.Bundle;
import android.accounts.Account;

public class MainActivity extends AppCompatActivity {
    private AccountManager accountManager;
    private DeveloperAccountManager developerAccountManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountManager = AccountManager.get(this);
        developerAccountManager = DeveloperAccountManager.getInstance(getApplicationContext());

    }
}