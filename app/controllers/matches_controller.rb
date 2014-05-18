class MatchesController < ApplicationController
	def new
	end

	def edit
	  @matches = Match.where(round_id: 1).order("id ASC")
	  @session = current_user
	end

	def update
	  	@match = Match.find(params[:id])
	  	if params[:match]['score_t1'].blank? or  !params[:match]['score_t1'].numeric?
			@team1_score_int = 0
		else
			@team1_score_int = Integer(params[:match]['score_t1'])
	  	end

	  	if params[:match]['score_t2'].blank? or  !params[:match]['score_t2'].numeric?
			@team2_score_int = 0
		else
			@team2_score_int = Integer(params[:match]['score_t2'])
	  	end

	  	
	  		if @match.update_attributes(:score_t1 => @team1_score_int,:score_t2 => @team2_score_int)
	  			if @team1_score_int > @team2_score_int
	  				@match.update_attributes(:winner_id => @match.team1.id,:winner_name => @match.team1.name,:played => 1)
	  			end

				if @team2_score_int > @team1_score_int
	  				@match.update_attributes(:winner_id => @match.team2.id,:winner_name => @match.team2.name,:played => 1)
	  			end

	  			if @team1_score_int >= 0 and  @team2_score_int >= 0 and  @team1_score_int == @team2_score_int
	  				@match.update_attributes(:played => 1)
	  			end

	    		redirect_to "/edit_matches##{@match.id}"
	 		else
	    		render 'edit'
	    	end
	   	end

	def show_matches
			@matches = Match.where('').order("id ASC")
			@session = current_user
			@active_matches = 'active'
			if !@session
  				redirect_to "/"
  			end
	end
end
class String
   def numeric?
    !(self =~ /^-?\d+(\.\d*)?$/).nil?
  end
end