package com.missa.tv360.ui.mobile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.missa.tv360.R
import com.missa.tv360.data.SearchRepository
import com.missa.tv360.data.Video
import com.missa.tv360.ui.DetailsFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MobileSearchFragment : Fragment() {
    private lateinit var adapter: MobileVideoAdapter
    private var searchJob: Job? = null
    private var requestVersion = 0L
    private var lastQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_mobile_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = MobileVideoAdapter(fillWidth = true, onClick = ::openVideo)
        view.findViewById<RecyclerView>(R.id.mobile_search_results).apply {
            layoutManager = GridLayoutManager(requireContext(), spanCount())
            adapter = this@MobileSearchFragment.adapter
        }
        val query = view.findViewById<EditText>(R.id.mobile_search_query)
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                view.findViewById<View>(R.id.mobile_search_clear).isVisible = !s.isNullOrBlank()
                scheduleSearch(s?.toString().orEmpty(), immediate = false)
            }
        })
        view.findViewById<View>(R.id.mobile_search_clear).setOnClickListener {
            query.text.clear()
            query.requestFocus()
        }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scheduleSearch(query.text.toString(), immediate = true)
                true
            } else false
        }
        view.findViewById<Button>(R.id.mobile_search_submit).setOnClickListener {
            scheduleSearch(query.text.toString(), immediate = true)
        }
        view.findViewById<Button>(R.id.mobile_search_retry).setOnClickListener {
            scheduleSearch(lastQuery, immediate = true)
        }
        view.findViewById<Button>(R.id.mobile_search_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        savedInstanceState?.getString(STATE_QUERY)?.let {
            query.setText(it)
            query.setSelection(it.length)
        }
        if (query.text.isBlank()) showInitial()
    }

    private fun scheduleSearch(rawQuery: String, immediate: Boolean) {
        val clean = rawQuery.trim()
        lastQuery = clean
        val version = ++requestVersion
        searchJob?.cancel()
        if (clean.isBlank()) {
            showInitial()
            return
        }
        setLoading()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (!immediate) delay(300)
            try {
                val outcome = SearchRepository.search(clean)
                if (!isAdded || version != requestVersion) return@launch
                renderOutcome(clean, outcome)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isAdded && version == requestVersion) showError()
            }
        }
    }

    private fun renderOutcome(query: String, outcome: SearchRepository.SearchOutcome) {
        adapter.submitList(outcome.items)
        setLoading(false)
        val status = view?.findViewById<TextView>(R.id.mobile_search_status) ?: return
        val retry = view?.findViewById<Button>(R.id.mobile_search_retry) ?: return
        if (outcome.items.isEmpty()) {
            status.text = getString(R.string.search_no_results)
            status.isVisible = true
            retry.isVisible = false
        } else {
            status.text = getString(R.string.search_results_header, query, outcome.items.size)
            status.isVisible = true
            retry.isVisible = false
        }
    }

    private fun showInitial() {
        adapter.submitList(emptyList())
        setLoading(false)
        view?.findViewById<TextView>(R.id.mobile_search_status)?.apply {
            text = getString(R.string.search_initial)
            isVisible = true
        }
        view?.findViewById<Button>(R.id.mobile_search_retry)?.isVisible = false
    }

    private fun showError() {
        setLoading(false)
        view?.findViewById<TextView>(R.id.mobile_search_status)?.apply {
            text = getString(R.string.search_error)
            isVisible = true
        }
        view?.findViewById<Button>(R.id.mobile_search_retry)?.isVisible = true
    }

    private fun setLoading(loading: Boolean = true) {
        view?.findViewById<ProgressBar>(R.id.mobile_search_progress)?.isVisible = loading
        if (loading) {
            view?.findViewById<TextView>(R.id.mobile_search_status)?.apply {
                text = getString(R.string.search_loading)
                isVisible = true
            }
            view?.findViewById<Button>(R.id.mobile_search_retry)?.isVisible = false
        }
    }

    private fun spanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (widthDp / 144f).toInt().coerceIn(2, 6)
    }

    private fun openVideo(video: Video) {
        if (video.isGamesLauncher) {
            startActivity(android.content.Intent(requireContext(), com.missa.tv360.games.GamesActivity::class.java))
        } else {
            openDetails(video)
        }
    }

    private fun openDetails(video: Video) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_container, DetailsFragment.newInstance(video))
            .addToBackStack("mobile-details")
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_QUERY, view?.findViewById<EditText>(R.id.mobile_search_query)?.text?.toString().orEmpty())
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val STATE_QUERY = "state_query"
    }
}
