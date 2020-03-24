package com.readrops.app.activities

import android.drm.DrmInfoRequest.ACCOUNT_ID
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.readrops.app.R
import com.readrops.app.adapters.NotificationPermissionAdapter
import com.readrops.app.databinding.ActivityNotificationPermissionBinding
import com.readrops.app.utils.Utils
import com.readrops.app.viewmodels.NotificationPermissionViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class NotificationPermissionActivity : AppCompatActivity() {

    lateinit var binding: ActivityNotificationPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_notification_permission)

        setTitle(R.string.notifications)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val accountId = intent.getIntExtra(ACCOUNT_ID, 0)
        val viewModel by viewModels<NotificationPermissionViewModel>()
        var adapter: NotificationPermissionAdapter? = null

        viewModel.getAccount(accountId).observe(this, Observer { account ->
            viewModel.account = account

            if (adapter == null) {
                // execute the following lines only once
                binding.notifPermissionAccountSwitch.isChecked = account.isNotificationsEnabled
                binding.notifPermissionAccountSwitch.setOnCheckedChangeListener { _, isChecked ->
                    account.isNotificationsEnabled = isChecked
                    adapter?.enableAll = isChecked
                    adapter?.notifyDataSetChanged()

                    viewModel.setAccountNotificationsState(isChecked)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnError { Utils.showSnackbar(binding.root, it.message) }
                            .subscribe()
                }

                adapter = NotificationPermissionAdapter(account.isNotificationsEnabled) { feed ->
                    viewModel.setFeedNotificationState(feed)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnError { Utils.showSnackbar(binding.root, it.message) }
                            .subscribe()
                }

                binding.notifPermissionAccountList.layoutManager = LinearLayoutManager(this)
                binding.notifPermissionAccountList.adapter = adapter

                viewModel.getFeedsWithNotifPermission().observe(this, Observer {
                    adapter?.submitList(it)
                })
            }

        })


    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }

        return super.onOptionsItemSelected(item)
    }
}