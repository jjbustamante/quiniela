class MatchesController < ApplicationController
	def new
	end

	def edit
		@matches = Match.where(round_id: 1).order("id ASC")
		@matches_round_oct = Match.where(round_id: 2).order("id ASC")
		@matches_round_cua = Match.where(round_id: 3).order("id ASC")
	    @matches_round_sem = Match.where(round_id: 4).order("id ASC")
		@matches_round_3er = Match.where(round_id: 5).order("id ASC")
		@matches_round_fin = Match.where(round_id: 6).order("id ASC")
	  	@allTeams = Team.where(status: 0).order("name asc")
	  	@session = current_user
	end

	def update
	  	@match = Match.find(params[:id])
	  	if params[:match]['score_t1'].blank? or  !params[:match]['score_t1'].numeric?
			@team1_score_int = -1
		else
			@team1_score_int = Integer(params[:match]['score_t1'])
	  	end

	  	if params[:match]['score_t2'].blank? or  !params[:match]['score_t2'].numeric?
			@team2_score_int = -1
		else
			@team2_score_int = Integer(params[:match]['score_t2'])
	  	end

	  	
	  		if @match.update_attributes(:team1_id=>params[:match]['team1_id'],:team2_id=>params[:match]['team2_id'],:winner_id=>params[:match]['winner_id'])
	  			if @team1_score_int > @team2_score_int
	  				@match.update_attributes(:score_t1 => @team1_score_int,:score_t2 => @team2_score_int,:winner_id => @match.team1.id,:winner_name => @match.team1.name,:played => 1)
	  			end

				if @team2_score_int > @team1_score_int
	  				@match.update_attributes(:score_t1 => @team1_score_int,:score_t2 => @team2_score_int,:winner_id => @match.team2.id,:winner_name => @match.team2.name,:played => 1)
	  			end

	  			if @team1_score_int >= 0 and  @team2_score_int >= 0 and  @team1_score_int == @team2_score_int
	  				@match.update_attributes(:score_t1 => @team1_score_int,:score_t2 => @team2_score_int,:played => 1)
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