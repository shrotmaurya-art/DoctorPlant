package com.plantcure.ai.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.plantcure.ai.R
import com.plantcure.ai.databinding.FragmentHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Scan History screen — shows all past plant disease scans.
 */
@AndroidEntryPoint
class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: ScanHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearchAndFilters()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ScanHistoryAdapter { scan ->
            // Navigate to Result screen to view details
            val action = HistoryFragmentDirections.actionHistoryToResult(
                imagePath = scan.imagePath,
                diseaseLabel = scan.diseaseName, // pass name for lookup
                confidence = scan.confidenceScore,
                scanId = scan.id
            )
            findNavController().navigate(action)
        }
        
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
        
        // Swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val scan = adapter.currentList[position]
                viewModel.deleteScan(scan)
                
                Snackbar.make(binding.root, R.string.scan_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) { viewModel.undoDelete(scan) }
                    .show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvHistory)
    }

    private fun setupSearchAndFilters() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.search(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { viewModel.search(it) }
                return true
            }
        })
        
        binding.chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            when (checkedIds.first()) {
                R.id.chipAll -> viewModel.clearFilters()
                R.id.chipFungal -> viewModel.filterByCause("Fungal")
                R.id.chipBacterial -> viewModel.filterByCause("Bacterial")
                R.id.chipHighSeverity -> viewModel.filterBySeverity("High")
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.scans.collect { scans ->
                        adapter.submitList(scans)
                        binding.layoutEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    viewModel.stats.collect { stats ->
                        stats?.let {
                            binding.tvTotalScans.text = it.totalScans.toString()
                            binding.tvMostCommon.text = it.mostCommonDisease ?: "-"
                            binding.tvThisMonth.text = it.scansThisMonth.toString()
                        }
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
