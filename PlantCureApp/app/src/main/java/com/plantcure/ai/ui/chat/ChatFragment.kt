package com.plantcure.ai.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.databinding.FragmentChatBinding
import com.plantcure.ai.databinding.ItemChatAiBinding
import com.plantcure.ai.databinding.ItemChatUserBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.rvChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChat.adapter = ChatAdapter(getDummyData())
        
        binding.btnSend.setOnClickListener {
            binding.etMessage.text.clear()
        }
    }

    private fun getDummyData(): List<ChatMessage> {
        return listOf(
            ChatMessage("Hello! I've analyzed your tomato plant scan. It shows signs of Late Blight with high severity. How can I help you manage this?", "10:30 AM", isUser = false),
            ChatMessage("What are the first steps I should take?", "10:31 AM", isUser = true),
            ChatMessage("First, isolate infected plants to prevent spreading. Remove and destroy heavily affected leaves. Avoid overhead watering.", "10:32 AM", isUser = false)
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

data class ChatMessage(val text: String, val timestamp: String, val isUser: Boolean)

class ChatAdapter(private val items: List<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_AI = 0
        const val VIEW_TYPE_USER = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
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
        if (holder is UserViewHolder) {
            holder.binding.tvMessage.text = item.text
            holder.binding.tvTimestamp.text = item.timestamp
        } else if (holder is AiViewHolder) {
            holder.binding.tvMessage.text = item.text
            holder.binding.tvTimestamp.text = item.timestamp
        }
    }

    override fun getItemCount() = items.size

    class UserViewHolder(val binding: ItemChatUserBinding) : RecyclerView.ViewHolder(binding.root)
    class AiViewHolder(val binding: ItemChatAiBinding) : RecyclerView.ViewHolder(binding.root)
}
