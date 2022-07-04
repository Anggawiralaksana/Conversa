package ai.prosa.conversa

import ai.prosa.conversa.common.data.model.UserInfo
import ai.prosa.conversa.common.exceptions.ConversaAPIFailedConnect
import ai.prosa.conversa.inapp.InappDialogueFragment
import ai.prosa.conversa.inapp.core.InappChat
import ai.prosa.conversa.inapp.exceptions.ConversaCannotConnectException
import ai.prosa.conversa.inapp.exceptions.ConversaInvalidCredentialsException
import ai.prosa.conversa.inapp.exceptions.RoomMembershipRequiredException
import ai.prosa.conversa.inapp.exceptions.RoomNotFoundException
import ai.prosa.conversa.omnichannel.OmniChannelDialogueFragment
import ai.prosa.conversa.omnichannel.core.OmniChannel
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
//        this.deleteDatabase("conversa_db")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.conversa_activity_main)
        InappChat.initialize(
            this,
            "IQGjZSR0mvwPMNDtpmUfllchHQN0zKFqbTEKppNrZ0Y",
            (application as MainApplication).roomMessageRepository,
            (application as MainApplication).templateMessageRepository
        )

        OmniChannel.initialize(
            "IQGjZSR0mvwPMNDtpmUfllchHQN0zKFqbTEKppNrZ0Y",
            (application as MainApplication).omniChannelMessageRepository
        )

//        toDialogue("customer1", "1")
//        toOmnichannel("tisoo")
    }

    private fun toOmnichannel(userid: String) {
        val password = userid
        val name = userid
        val avatarUrl = "https://www.w3schools.com/w3images/avatar5.png"

        lifecycleScope.launch(Dispatchers.IO) {
            OmniChannel
                .setUser(
                    this@MainActivity,
                    userid,
                    password,
                    name,
                    avatarUrl
                )

            val args = Bundle()
            args.putSerializable(
                OmniChannelDialogueFragment.USER_TAG, UserInfo(
                    userid,
                    name,
                    true,
                    avatarUrl
                )
            )

            val toFragment: Fragment = OmniChannelDialogueFragment()
            toFragment.arguments = args
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.nav_host_fragment_container,
                    toFragment,
                    OmniChannelDialogueFragment::class.java.name
                )
                .addToBackStack(OmniChannelDialogueFragment::class.java.name).commit()
        }
    }

    private fun toDialogue(userid: String, roomid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val password = "${userid}_password"

            try {
                val room = InappChat
                    .setUser(userid, password, userid)
                    .setRoom(roomid)
                val history = room.getHistory()
                Log.d(TAG, "onCreate: HISTORY [CACHE] ${history.cachedHistory.size}")

                val args = Bundle()
                args.putSerializable(
                    InappDialogueFragment.USER_1_TAG, UserInfo(
                        "customer1",
                        "Customer 1",
                        true,
                        "https://www.w3schools.com/w3images/avatar5.png"
                    )
                )
                args.putSerializable(
                    InappDialogueFragment.USER_2_TAG, UserInfo(
                        "driver1",
                        "The Driver",
                        false,
                        "https://www.w3schools.com/w3images/avatar5.png",
                        "BlueBird Driver - B 1234 AB"
                    )
                )

                val toFragment: Fragment = InappDialogueFragment()
                toFragment.arguments = args
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.nav_host_fragment_container,
                        toFragment,
                        InappDialogueFragment::class.java.name
                    )
                    .addToBackStack(InappDialogueFragment::class.java.name).commit()

                // Periodically broadcast state
                val delayMs: Long = 60000
                val handler = Handler(Looper.getMainLooper())
                val runnable = object : Runnable {
                    override fun run() {
                        if (InappChat.room!!.disableChatUI.value!!) {
                            InappChat.room!!.enableChatUI()
                        } else {
                            InappChat.room!!.disableChatUI()
                        }
                        handler.postDelayed(this, delayMs)
                    }
                }
                handler.postDelayed(runnable, delayMs)

            } catch (e: ConversaCannotConnectException) {
                Log.d(TAG, "inappLogin: ${e.localizedMessage}")
            } catch (e: ConversaInvalidCredentialsException) {
                Log.d(TAG, "inappLogin: ${e.localizedMessage}")
            } catch (e: RoomNotFoundException) {
                Log.d(TAG, "inappLogin: ${e.localizedMessage}")
            } catch (e: RoomMembershipRequiredException) {
                Log.d(TAG, "inappLogin: ${e.localizedMessage}")
            } catch (e: ConversaAPIFailedConnect) {
                Log.d(TAG, "inappLogin: ${e.localizedMessage}")
            }
        }
    }

    companion object {
        const val MESSAGE_CHANNEL_ID = "ConversaMessageChannelID"
        const val TAG = "MainActivity"
    }
}