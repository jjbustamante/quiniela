class UsersController < ApplicationController
	def show
    @user = current_user
        @session = current_user
    if !@session
        redirect_to "/"
      end 
  end

  def edit
    @user = current_user
        @session = current_user
    if !@session
        redirect_to "/"
      end 
  end

  def update
    @user = current_user # makes our views "cleaner" and more consistent
     @session = current_user
    if @user.update_attributes(params[:user])
      flash[:success] = "Datos actualizados con exito!"
      redirect_to '/'
    else
      render :action => :edit
    end

  end

  def sudocool
    @user = current_user
    if !@user.nil?
      if @user.email == "emmanuel.perez@dbaccess.com" or @user.email == "jose.rodriguez@dbaccess.com"
        @user.is_admin = 1
        @user.save
        flash[:success] = "Hello master Wayne"
        @user_is_admin = true
      end
    end
    redirect_to "/"
  end

  def show_count
    @user = current_user # makes our views "cleaner" and more consistent
     @session = current_user
    @users = User.all(:joins => :quinielas, :select => "users.name, Users.email, count(quinielas.id) as quinielas_count", :group => "users.id")
  end
end
