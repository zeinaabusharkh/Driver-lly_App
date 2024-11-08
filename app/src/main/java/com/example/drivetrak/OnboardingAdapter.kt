package com.example.drivetrak

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(private val context: Context) : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    private val steps = listOf(
        OnboardingStep(R.drawable.one_slide, "Welcome", "This is the first step of the onboarding tutorial."),
        OnboardingStep(R.drawable.five_slide, "Track Trips", "Here you can start tracking your trips."),
        OnboardingStep(R.drawable.four_slide, "Get Feedback", "Our AI will provide feedback for each trip."),
        OnboardingStep(R.drawable.siz_slide, "View Stats", "Check your driving performance stats anytime.")
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.carousel_item, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val step = steps[position]
        holder.title.text = step.title
        holder.description.text = step.description
        holder.image.setImageResource(step.imageRes)
    }

    override fun getItemCount(): Int = steps.size

    inner class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.onboarding_image)
        val title: TextView = itemView.findViewById(R.id.step_title)
        val description: TextView = itemView.findViewById(R.id.step_description)
    }

    data class OnboardingStep(val imageRes: Int, val title: String, val description: String)
}
