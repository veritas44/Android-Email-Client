package com.email.scenes.search.data

import com.email.R
import com.email.bgworker.BackgroundWorker
import com.email.bgworker.ProgressReporter
import com.email.db.SearchLocalDB
import com.email.scenes.mailbox.data.EmailThread
import com.email.scenes.mailbox.data.LoadParams
import com.email.utils.UIMessage

class SearchEmailWorker(
        private val userEmail: String,
        private val db: SearchLocalDB,
        private val queryText: String,
        private val loadParams: LoadParams,
        override val publishFn: (
                SearchResult.SearchEmails) -> Unit
): BackgroundWorker<SearchResult.SearchEmails> {

    override val canBeParallelized = true

    override fun catchException(ex: Exception): SearchResult.SearchEmails {
        return SearchResult.SearchEmails.Failure(UIMessage(resId = R.string.failed_searching_emails))
    }

    private fun loadThreadsWithParams(): List<EmailThread> = when (loadParams) {
        is LoadParams.NewPage -> db.searchMailsInDB(
                queryText = queryText,
                startDate = loadParams.startDate,
                limit = loadParams.size,
                userEmail = userEmail)
        is LoadParams.Reset -> db.searchMailsInDB(
                queryText = queryText,
                startDate = null,
                limit = loadParams.size,
                userEmail = userEmail)
    }

    override fun work(reporter: ProgressReporter<SearchResult.SearchEmails>): SearchResult.SearchEmails? {
        val emailThreads = loadThreadsWithParams()

        return SearchResult.SearchEmails.Success(
                emailThreads = emailThreads,
                isReset = loadParams is LoadParams.Reset,
                queryText = queryText)
    }

    override fun cancel() {
    }

}