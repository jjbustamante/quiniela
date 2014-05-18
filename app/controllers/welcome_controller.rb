class WelcomeController < ApplicationController
  def index
  	@user_session = UserSession.new

  	@session = current_user
  	@active_index = 'active'
  	if @session
  		@quinielas = Quiniela.where(user_id: @session.id).order("points DESC")
	end
  end
end
