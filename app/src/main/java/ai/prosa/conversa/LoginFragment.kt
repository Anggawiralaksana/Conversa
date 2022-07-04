package ai.prosa.conversa

import ai.prosa.conversa.common.data.model.UserInfo
import ai.prosa.conversa.common.exceptions.ConversaAPIFailedConnect
import ai.prosa.conversa.databinding.ConversaFragmentLoginBinding
import ai.prosa.conversa.inapp.core.InappChat
import ai.prosa.conversa.inapp.data.model.InappCache
import ai.prosa.conversa.inapp.data.sharedPreferences.InappPreferenceHelper
import ai.prosa.conversa.inapp.exceptions.ConversaCannotConnectException
import ai.prosa.conversa.inapp.exceptions.ConversaInvalidCredentialsException
import ai.prosa.conversa.inapp.exceptions.RoomMembershipRequiredException
import ai.prosa.conversa.inapp.exceptions.RoomNotFoundException
import ai.prosa.conversa.omnichannel.core.OmniChannel
import ai.prosa.conversa.omnichannel.data.model.OmniChannelCache
import ai.prosa.conversa.omnichannel.data.sharedPreferences.OmniChannelPreferenceHelper
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


enum class Module {
    INAPP,
    OMNICHANNEL
}

fun String.toModule(): Module? {
    if (this.lowercase() == "inapp") {
        return Module.INAPP
    } else if (this.lowercase() == "omnichannel") {
        return Module.OMNICHANNEL
    }
    return null
}

class LoginFragment : Fragment() {
    private var _binding: ConversaFragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        InappPreferenceHelper(requireContext()).clearCache()
        OmniChannelPreferenceHelper(requireContext()).clearCache()
//
        _binding = ConversaFragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadingOff()
        when (getModule()) {
            Module.INAPP -> {
                binding.radio.check(R.id.radio_inapp)
                inappLogin(view)
            }
            Module.OMNICHANNEL -> {
                binding.radio.check(R.id.radio_omnichannel)
                omniChannelLogin(view, savedInstanceState)
            }
            else -> binding.radio.clearCheck()
        }
        binding.radio.setOnCheckedChangeListener { _, pos ->
            when (pos) {
                R.id.radio_inapp -> {
                    inappLogin(view)
                    setModule(Module.INAPP)
                }
                R.id.radio_omnichannel -> {
                    omniChannelLogin(view, savedInstanceState)
                    setModule(Module.OMNICHANNEL)
                }
            }
        }
    }

    private fun inappLogin(view: View) {
        Log.d(TAG, "omniChannelLogin: INAPP")
        binding.roomId.visibility = View.VISIBLE

        // TODO: don't hard code this
        val host = BuildConfig.XMPP_HOST
        val port = 5222
        val domain = "localhost"

        val cache = InappPreferenceHelper(requireContext()).getCache()
        Log.d(TAG, "inappLogin: [CACHE] $cache")

        binding.btnLogin.setOnClickListener {
            val userid = binding.userid.editText!!.text.toString().trim()
            val roomid = binding.roomId.editText!!.text.toString().trim()
            val name = userid.replaceFirstChar { it.uppercase() }
            inappLogin(view, cache, host, port, domain, userid, name, roomid)
        }

        if (!cache.containsEmpty()) {
            inappLogin(
                view,
                cache,
                host,
                port,
                domain,
                cache.userid,
                cache.name,
                cache.roomid
            )
        }
    }

    private fun inappLogin(
        view: View,
        cache: InappCache,
        host: String,
        port: Int,
        domain: String,
        userid: String,
        name: String,
        roomid: String
    ) {
        loadingOn()
        if (cache.userid.isEmpty() || cache.password.isEmpty() || host != cache.host || port != cache.port || domain != cache.domain || userid != cache.userid || roomid != cache.roomid) {
            Log.d(TAG, "onCreate: NO USERNAME")
            cache.userid = userid
            cache.name = name
            cache.host = BuildConfig.XMPP_HOST
            cache.port = BuildConfig.XMPP_PORT
            cache.domain = BuildConfig.XMPP_DOMAIN
            cache.roomid = roomid

            lifecycleScope.launch {
                try {
                    cache.password = "password"

                    val room = InappChat
                        .setUser(
                            userid = cache.userid,
                            password = cache.password,
                            name = cache.userid,
                            avatarUrl = ""
                        )
                        .setRoom(roomid)

                    InappPreferenceHelper(requireContext()).saveCache(cache)
                    loadingOff()

                    lifecycleScope.launch(Dispatchers.IO) {
                        room.getHistory()
                        delay(500)
                    }.join()
//
//                    // Periodically broadcast state
//                    val delayMs: Long = 10000
//                    val handler = Handler(Looper.getMainLooper())
//                    val runnable = object : Runnable {
//                        override fun run() {
//                            if (InappChat.room!!.disableChatUI.value!!) {
//                                InappChat.room!!.enableChatUI()
//                            } else {
//                                InappChat.room!!.disableChatUI()
//                            }
//                            handler.postDelayed(this, delayMs)
//                        }
//                    }
//                    handler.postDelayed(runnable, delayMs)
//
//                    InappChat.room!!.unreadMessagesCount.observe(viewLifecycleOwner) { unreadCount ->
//                        Log.d(TAG, "inappLogin: [UNREAD MESSAGES COUNT] $unreadCount")
//                    }

                    navigateToInappDialogue(view, cache.userid, cache.name)
                } catch (e: ConversaCannotConnectException) {
                    loadingOff()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                } catch (e: ConversaInvalidCredentialsException) {
                    loadingOff()
                    InappPreferenceHelper(requireContext()).clearCache()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                } catch (e: RoomNotFoundException) {
                    loadingOff()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                } catch (e: RoomMembershipRequiredException) {
                    loadingOff()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                } catch (e: ConversaAPIFailedConnect) {
                    loadingOff()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            lifecycleScope.launch {
                Log.d(TAG, "inappLogin: [CACHED]")
                try {
                    val room = InappChat
                        .setUser(cache.userid, cache.password, cache.userid)
                        .setRoom(roomid)

                    lifecycleScope.launch(Dispatchers.IO) {
                        room.getHistory()
                    }
//
//                    InappChat.room!!.unreadMessagesCount.observe(viewLifecycleOwner) { unreadCount ->
//                        Log.d(TAG, "inappLogin: [UNREAD MESSAGES COUNT] $unreadCount")
//                    }
//
//                    // Periodically broadcast state
//                    val delayMs: Long = 10000
//                    val handler = Handler(Looper.getMainLooper())
//                    val runnable = object : Runnable {
//                        override fun run() {
//                            if (InappChat.room!!.disableChatUI.value!!) {
//                                InappChat.room!!.enableChatUI()
//                            } else {
//                                InappChat.room!!.disableChatUI()
//                            }
//                            handler.postDelayed(this, delayMs)
//                        }
//                    }
//                    handler.postDelayed(runnable, delayMs)
//
//                    InappChat.room!!.unreadMessagesCount.observe(viewLifecycleOwner) { unreadCount ->
//                        Log.d(TAG, "inappLogin: [UNREAD MESSAGES COUNT] $unreadCount")
//                    }

                    navigateToInappDialogue(view, cache.userid, cache.name)
                } catch (e: ConversaCannotConnectException) {
                    loadingOff()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                } catch (e: ConversaInvalidCredentialsException) {
                    loadingOff()
                    InappPreferenceHelper(requireContext()).clearCache()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                } catch (e: RoomNotFoundException) {
                    loadingOff()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                } catch (e: RoomMembershipRequiredException) {
                    loadingOff()
                    Log.d(TAG, "inappLogin: ${e.localizedMessage}")
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun omniChannelLogin(view: View, savedInstanceState: Bundle?) {
        binding.roomId.visibility = View.GONE

        Log.d(TAG, "omniChannelLogin: AAAAAAAAAAA")
        // TODO: don't hardcode application ID
        val applicationID = "IQGjZSR0mvwPMNDtpmUfllchHQN0zKFqbTEKppNrZ0Y"

        binding.btnLogin.setOnClickListener {
            val userid = binding.userid.editText!!.text.toString().trim()
            val name = userid.replaceFirstChar { it.uppercase() }
            val password = userid

            lifecycleScope.launch {
                try {
                    OmniChannel
                        .setUser(
                            requireContext(),
                            userid,
                            password,
                            name,
                            "https://avatar.png"
                        )

                    // Navigation Controller
                    val action =
                        LoginFragmentDirections.actionLoginToOmniChannelDialogue(
                            UserInfo(
                                userid,
                                name,
                                false,
                                "https://www.w3schools.com/w3images/avatar5.png"
                            )
                        )
                    view.findNavController().navigate(action)
                } catch (e: ConversaInvalidCredentialsException) {
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun omniChannelLogin(
        view: View,
        cache: OmniChannelCache,
        userID: String,
        username: String,
        applicationID: String
    ) {
        val omniChannelCache = OmniChannelPreferenceHelper(requireContext()).getCache()
        Log.d(TAG, "omniChannelLogin: OMNICHANNEL $omniChannelCache")

        loadingOn()
        if (omniChannelCache.userid.isEmpty() || omniChannelCache.accessToken.isEmpty() || userID != cache.userid) {
            binding.roomId.visibility = View.GONE

            lifecycleScope.launch {
//                ConversaApi.omnichannelAuth(userID, userID, username, applicationID) {
//                    Log.d(TAG, "omniChannelLogin: $it")
//                    cache.accessToken = it.accessToken
//                    cache.refreshToken = it.refreshToken
//                    cache.name = username
//                    cache.userid = userID
//                    OmniChannelPreferenceHelper(requireContext()).saveCache(cache)
//                    omniChannelViewModel.socketConnect(cache.accessToken)
//                    loadingOff()
//                    navigateToOmniChannelDialogue(view, userID, username)
//                }
            }
        } else {
//            ConversaApi.checkToken(cache.accessToken) {
//                if (it == JWTStatus.VALID) {
//                    Log.d(TAG, "onCreate: ACCESS TOKEN VALID")
//                    Log.d(TAG, "onCreate: ${cache.accessToken}")
//                    omniChannelViewModel.socketConnect(cache.accessToken)
//                    loadingOff()
//                    navigateToOmniChannelDialogue(view, userID, username)
//                } else {
//                    ConversaApi.refreshToken(cache.refreshToken) { status, accessToken ->
//                        if (status == JWTStatus.VALID) {
//                            cache.accessToken = accessToken
//                            OmniChannelPreferenceHelper(requireContext()).saveCache(cache)
//                            omniChannelViewModel.socketConnect(cache.accessToken)
//                        } else {
//                            lifecycleScope.launch {
////                                omniChannelResetToken(userID, username, applicationID)
//                            }
//                            loadingOff()
//                            navigateToOmniChannelDialogue(view, userID, username)
//                        }
//                    }
//                }
//            }
        }
    }

    private fun navigateToOmniChannelDialogue(view: View, userID: String, name: String) {
        val action =
            LoginFragmentDirections.actionLoginToOmniChannelDialogue(
                UserInfo(
                    userID,
                    name,
                    !userID.lowercase().contains("driver"),
                    "https://www.w3schools.com/w3images/avatar5.png"
                )
            )
        view.findNavController().navigate(action)
    }

    private fun navigateToInappDialogue(view: View, userID: String, name: String) {

        // Customer
        val action = if (!userID.lowercase().contains("driver")) {
            LoginFragmentDirections.actionLoginToInappDialogue(
                UserInfo(
                    userID,
                    name,
                    true,
                    "http://via.placeholder.com/300.png"
                ),
                UserInfo(
                    "",
                    "The Driver",
                    false,
                    "https://www.w3schools.com/w3images/avatar5.png",
                    "BlueBird Driver - B 1234 AB"
                )
            )
        } else {
            LoginFragmentDirections.actionLoginToInappDialogue(
                UserInfo(
                    userID,
                    name,
                    false,
                    "http://via.placeholder.com/300.png"
                ),
                UserInfo(
                    "",
                    "The Customer",
                    true,
                    "https://www.w3schools.com/w3images/avatar5.png",
                    "Customer"
                )
            )
        }

        Log.d(TAG, "navigateToInappDialogue: ${InappChat.room!!.unreadMessagesCount.value}")
        view.findNavController().navigate(action)
    }


    private fun getModule(): Module? {
        val sharedPref = requireContext().getSharedPreferences(
            requireContext().getString(R.string.PREF_LOGIN_FILE),
            Context.MODE_PRIVATE
        )
        return (sharedPref.getString(requireContext().getString(R.string.PREF_LOGIN_MODULE), "")
            ?: "").toModule()
    }

    private fun setModule(module: Module) {
        val sharedPref = requireContext().getSharedPreferences(
            requireContext().getString(R.string.PREF_LOGIN_FILE),
            Context.MODE_PRIVATE
        )

        with(sharedPref.edit()) {
            putString(
                requireContext().getString(R.string.PREF_LOGIN_MODULE),
                module.toString()
            )
            apply()
        }
    }

    private fun loadingOn() {
        binding.btnLogin.text = "Logging In ..."
        binding.btnLogin.alpha = .5f
        binding.btnLogin.isClickable = false
        binding.btnLogin.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

    }

    private fun loadingOff() {
        binding.btnLogin.text = "Login"
        binding.btnLogin.alpha = 1f
        binding.btnLogin.isClickable = true
        binding.btnLogin.isEnabled = true
        binding.progressBar.visibility = View.INVISIBLE
    }

    companion object {
        private const val TAG = "ConversaLogin"

    }
}