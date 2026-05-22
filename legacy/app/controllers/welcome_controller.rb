class WelcomeController < ApplicationController
  def index
  	@user_session = UserSession.new
  	@session = current_user

  end
end
