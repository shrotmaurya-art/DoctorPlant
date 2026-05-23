package com.plantcure.ai.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.databinding.FragmentChatBinding
import com.plantcure.ai.databinding.ItemChatAiBinding
import com.plantcure.ai.databinding.ItemChatUserBinding
import com.plantcure.ai.domain.model.ChatMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        setupRecyclerView()
        setupSendButton()
        observeMessages()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChat.adapter = adapter
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString() ?: return@setOnClickListener
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.etMessage.text?.clear()
            }
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length == 1 && before == 0) {
                    binding.btnSend.animate()
                        .rotation(-45f)
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .start()
                } else if (s.isNullOrBlank() && before > 0) {
                    binding.btnSend.animate()
                        .rotation(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
            }
        })
    }

    private fun observeMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitMessages(messages)
                        // Scroll to bottom
                        if (messages.isNotEmpty()) {
                            binding.rvChat.scrollToPosition(messages.size - 1)
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.btnSend.isEnabled = !loading
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<ChatMessage> = emptyList()

    companion object {
        const val VIEW_TYPE_AI = 0
        const val VIEW_TYPE_USER = 1
    }

    fun submitMessages(newItems: List<ChatMessage>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].role == "user") VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserViewHolder(ItemChatUserBinding.inflate(inflater, parent, false))
        } else {
            AiViewHolder(ItemChatAiBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeText = timeFormat.format(Date(item.timestamp))

        if (holder is UserViewHolder) {
            holder.binding.tvMessage.text = item.text
            holder.binding.tvTimestamp.text = timeText
        } else if (holder is AiViewHolder) {
            val oldAnimator = holder.itemView.tag as? android.animation.ObjectAnimator
            oldAnimator?.cancel()
            
            if (item.isLoading) {
                holder.binding.tvMessage.text = "Thinking..."
                holder.binding.tvMessage.alpha = 0.6f
                
                val bounceAnimator = android.animation.ObjectAnimator.ofFloat(holder.binding.tvMessage, "translationY", 0f, -10f, 0f).apply {
                    duration = 600
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    start()
                }
                holder.itemView.tag = bounceAnimator
            } else {
                holder.binding.tvMessage.translationY = 0f
                holder.binding.tvMessage.text = item.text
                holder.binding.tvMessage.alpha = 1.0f
                holder.itemView.tag = null
            }
        }

        // Bubble scale up
        holder.itemView.scaleX = 0.8f
        holder.itemView.scaleY = 0.8f
        holder.itemView.alpha = 0f
        holder.itemView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
    }

    override fun getItemCount() = items.size

    class UserViewHolder(val binding: ItemChatUserBinding) : RecyclerView.ViewHolder(binding.root)
    class AiViewHolder(val binding: ItemChatAiBinding) : RecyclerView.ViewHolder(binding.root)
}
